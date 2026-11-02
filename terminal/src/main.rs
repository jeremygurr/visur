// Visur's terminal client. One process, one thread, one loop, and no editor state: it paints the viewport the
// server sends and forwards keys. See docs/wayfinder/renderer-delivery/spec.md §5 and the component design.

mod caps;
mod keys;
mod lifecycle;
mod paint;
mod protocol;
mod screen;
mod transport;
mod zseq;

use crossterm::event::{self, Event};
use crossterm::terminal;
use std::io::Write;
use std::os::unix::io::FromRawFd;
use std::time::{Duration, Instant};
use zseq::{Effect, Pending};

/// `visur` exits 0 to detach (leave the server running) and this code to quit (stop the one it started).
/// The launcher reads it.
const QUIT: i32 = 3;
/// The server hung up under us. Any non-zero code tells the launcher to stop a server it started; this one says why.
const SERVER_GONE: i32 = 4;

fn main() {
  let mut socket = match transport::connect() {
    Ok(socket) => socket,
    Err(error) => {
      eprintln!("cannot reach the server at {}: {error}\nstart one with ./gradlew run", transport::URL);
      std::process::exit(1);
    }
  };

  let (cols, rows) = size();
  // Content rows only: the last row is the client's own mode line, so it is not the server's to fill.
  transport::declare(&mut socket, cols, rows.saturating_sub(1));

  terminal::enable_raw_mode().expect("raw mode");
  // fd 1 unbuffered, not `std::io::Stdout`: that is line-buffered here, so an 18 KB full repaint would be written in
  // a dozen `write(2)` calls instead of one, and a frame can then land on the terminal in pieces (spec §5.5).
  let tty = std::mem::ManuallyDrop::new(unsafe { std::fs::File::from_raw_fd(1) });
  let mut out = &*tty;
  out.write_all(b"\x1b[?1049h\x1b[?2004h").ok(); // alternate screen and bracketed paste
  out.write_all(caps::PUSH.as_bytes()).ok(); // unambiguous `Esc`, where the terminal understands it
  out.flush().ok();

  // Every way out, including a panic, hands the terminal back through this guard (spec §5.5).
  let mut restore = lifecycle::Restore::new(&*tty);

  let sync_output = probe_sync_output();
  let (cols, rows) = redeclare_if_resized(&mut socket, cols, rows);

  let code = run(&mut socket, &mut out, cols, rows, sync_output == caps::Sync::Supported);

  // `process::exit` skips destructors, so the guard has to be spent by hand on the way out.
  restore.now();
  std::process::exit(code);
}

fn size() -> (usize, usize) {
  terminal::size()
    .map(|(cols, rows)| (cols as usize, rows as usize))
    .unwrap_or((100, 30))
}

/// How long the terminal gets to answer the capability probe before the client assumes nothing.
const CAPS_TIMEOUT: Duration = Duration::from_millis(60);

/// Ask whether the terminal has synchronized output (`DECRQM`), and wait briefly for the answer.
///
/// The answer is not a key event, so crossterm's reader would swallow it: this reads fd 0 directly, before the loop
/// starts and while nothing else is reading. shortcut: bytes typed inside the probe window are consumed along with the
/// answer; upgrade by handing the leftovers to crossterm's parser once its buffer is reachable.
fn probe_sync_output() -> caps::Sync {
  // Wrapped so dropping it does not close fd 1 under the rest of the client.
  let tty = std::mem::ManuallyDrop::new(unsafe { std::fs::File::from_raw_fd(1) });
  let _ = (&*tty).write_all(caps::PROBE.as_bytes());

  let deadline = Instant::now() + CAPS_TIMEOUT;
  let mut reply: Vec<u8> = Vec::new();
  let mut buffer = [0u8; 64];
  while Instant::now() < deadline {
    let mut polled = libc::pollfd { fd: libc::STDIN_FILENO, events: libc::POLLIN, revents: 0 };
    if unsafe { libc::poll(&mut polled, 1, 5) } <= 0 {
      continue;
    }
    let count = unsafe { libc::read(libc::STDIN_FILENO, buffer.as_mut_ptr().cast(), buffer.len()) };
    if count <= 0 {
      continue;
    }
    reply.extend_from_slice(&buffer[..count as usize]);
    if reply.windows(2).any(|pair| pair == b"$y") {
      break; // the answer is in; no need to wait out the rest of the window
    }
  }
  caps::sync_output(&String::from_utf8_lossy(&reply))
}

/// Close the probe window's blind spot: crossterm installs its `SIGWINCH` handler on the first poll, which happens after
/// the probe, so a resize during those milliseconds is a signal nobody was listening for. The size is the source of
/// truth, so ask it.
fn redeclare_if_resized(socket: &mut transport::Socket, cols: usize, rows: usize) -> (usize, usize) {
  let now = size();
  if now != (cols, rows) {
    transport::declare(socket, now.0, now.1.saturating_sub(1));
  }
  now
}

/// The loop: take the newest view, take the keys, paint what changed. Nothing here interprets the document.
fn run(socket: &mut transport::Socket, out: &mut impl Write, mut cols: usize, mut rows: usize, sync: bool) -> i32 {
  let mut view = protocol::ViewUpdate::default();
  // The only cached state in the client, and the only thing a diff needs: what the terminal is already showing.
  let mut shown: Option<screen::Grid> = None;
  let mut needs_paint = true;
  let mut pending = Pending::None;

  loop {
    match take_view(socket) {
      // The socket is gone: there is no server left to paint for, so the terminal goes back to the shell now rather
      // than spinning on a dead socket until somebody kills the client.
      Taken::Gone => return SERVER_GONE,
      Taken::Views(Some(newer)) => {
        view = newer;
        needs_paint = true;
      }
      Taken::Views(None) => {}
    }

    if event::poll(Duration::ZERO).unwrap_or(false) {
      // Exactly one read per poll: reading twice here would swallow every other keystroke.
      if let Ok(event) = event::read() {
        if let Event::Resize(new_cols, new_rows) = event {
          // The rows already painted are the wrong shape now. Declare the new one: a declaration always earns a
          // `full: true` answer, so the server resends rather than the client guessing at the old rows.
          cols = new_cols as usize;
          rows = new_rows as usize;
          transport::declare(socket, cols, rows.saturating_sub(1));
          needs_paint = true;
        } else if let Step::Finish(effect) = act(socket, &mut pending, event) {
          return finish(effect);
        }
      }
    }

    if needs_paint {
      // One write per frame, and synchronized output when the terminal has it: a half-painted screen is what tearing
      // looks like (spec §5.5).
      let grid = screen::compose(&view, cols, rows);
      let frame = paint::frame(shown.as_ref(), &grid, view.full);
      let frame = if sync {
        format!("\x1b[?2026h{frame}\x1b[?2026l")
      } else {
        frame
      };
      out.write_all(frame.as_bytes()).ok();
      out.flush().ok();
      shown = Some(grid);
      needs_paint = false;
    }
  }
}

enum Step {
  Continue,
  Finish(Effect),
}

/// One event in, at most one key on the wire.
fn act(socket: &mut transport::Socket, pending: &mut Pending, event: Event) -> Step {
  let trace = std::env::var_os("VISUR_TRACE").is_some();
  if trace {
    eprintln!("\r\ntrace: event {:?}", event);
  }
  let event = match event {
    Event::Paste(text) => {
      transport::send_paste(socket, &text);
      return Step::Continue;
    }
    other => other,
  };
  let Some(token) = keys::action(event) else { return Step::Continue };
  if trace {
    eprintln!("\r\ntrace: token {token:?}");
  }
  let (next, effect) = zseq::resolve(*pending, &token);
  *pending = next;
  match effect {
    Effect::Forward(token) => {
      transport::send_key(socket, &token);
      Step::Continue
    }
    Effect::Nothing => Step::Continue,
    quit => Step::Finish(quit),
  }
}

fn finish(effect: Effect) -> i32 {
  match effect {
    Effect::Quit => QUIT,
    _ => 0,
  }
}

/// What one drain of the socket produced.
enum Taken {
  /// Whatever views were waiting; the newest of them, if any.
  Views(Option<protocol::ViewUpdate>),
  /// The server hung up. Not the same as "nothing waiting": one is idle, the other is over.
  Gone,
}

fn take_view(socket: &mut transport::Socket) -> Taken {
  let mut newest = None;
  loop {
    match transport::next(socket) {
      transport::Incoming::Frame(frame) => {
        if let Some(view) = protocol::parse(&frame) {
          newest = Some(view);
        }
      }
      transport::Incoming::Idle => return Taken::Views(newest),
      transport::Incoming::Closed => return Taken::Gone,
    }
  }
}
