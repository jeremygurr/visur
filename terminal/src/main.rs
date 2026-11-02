// Visur's terminal client. One process, one thread, one loop, and no editor state: it paints the viewport the
// server sends and forwards keys. See docs/wayfinder/renderer-delivery/spec.md §5 and the component design.

mod keys;
mod paint;
mod protocol;
mod screen;
mod transport;
mod zseq;

use crossterm::event::{self, Event};
use crossterm::terminal;
use std::io::Write;
use std::time::Duration;
use zseq::{Effect, Pending};

/// `visur` exits 0 to detach (leave the server running) and this code to quit (stop the one it started).
/// The launcher reads it; the difference is meaningless until there is a launcher (ticket 08).
const QUIT: i32 = 3;

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
  let mut out = std::io::stdout();
  out.write_all(b"\x1b[?1049h").ok(); // alternate screen: this client owns the whole terminal

  let code = run(&mut socket, &mut out, cols, rows);

  out.write_all(b"\x1b[0m\x1b[?25h\x1b[?1049l").ok();
  out.flush().ok();
  terminal::disable_raw_mode().ok();
  std::process::exit(code);
}

fn size() -> (usize, usize) {
  terminal::size()
    .map(|(cols, rows)| (cols as usize, rows as usize))
    .unwrap_or((100, 30))
}

/// The loop: take the newest view, take the keys, paint what changed. Nothing here interprets the document.
fn run(socket: &mut transport::Socket, out: &mut impl Write, cols: usize, rows: usize) -> i32 {
  let mut view = protocol::ViewUpdate::default();
  let mut painted = false;
  let mut pending = Pending::None;

  loop {
    match take_view(socket) {
      Some(newer) => {
        view = newer;
        painted = false;
      }
      None => {}
    }

    if event::poll(Duration::ZERO).unwrap_or(false) {
      // Exactly one read per poll: reading twice here would swallow every other keystroke.
      if let Ok(event) = event::read() {
        if let Step::Finish(effect) = act(socket, &mut pending, event) {
          return finish(effect);
        }
      }
    }

    if !painted {
      let frame = paint::full(&screen::compose(&view, cols, rows));
      out.write_all(frame.as_bytes()).ok();
      out.flush().ok();
      painted = true;
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

/// The newest view waiting on the socket, if any: a stale frame is worth skipping, not painting.
fn take_view(socket: &mut transport::Socket) -> Option<protocol::ViewUpdate> {
  let mut newest = None;
  loop {
    match transport::next(socket) {
      transport::Incoming::Frame(frame) => {
        if let Some(view) = protocol::parse(&frame) {
          newest = Some(view);
        }
      }
      transport::Incoming::Idle => return newest,
      transport::Incoming::Closed => return newest,
    }
  }
}
