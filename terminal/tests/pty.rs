//! End-to-end: drive the real client in a real pty, against a real server, and read the escape stream back.
//!
//! This is the successor to the spike's `drive.py`. It is Rust because everything here is Rust, so the acceptance
//! check lives next to the code it checks and needs no second language in the repo.
//!
//! The server keeps the document in one global editor model, so these tests share state with every other client:
//! run them serially, against a freshly started server. With no server listening the run is reported and skipped
//! rather than silently passing.
//!
//! ```text
//! VISUR_E2E=1 cargo test --test pty -- --nocapture --test-threads=1
//! ```

use std::fs::File;
use std::io::{Read, Write};
use std::os::unix::io::{AsRawFd, FromRawFd, RawFd};
use std::process::{Child, Command, ExitStatus, Stdio};
use std::sync::{Arc, Mutex};
use std::time::{Duration, Instant};

/// The window the checks drive. Overridable so the conformance sweep can measure a second viewport size.
fn cols() -> u16 {
  std::env::var("VISUR_PTY_COLS").ok().and_then(|value| value.parse().ok()).unwrap_or(100)
}

fn rows() -> u16 {
  std::env::var("VISUR_PTY_ROWS").ok().and_then(|value| value.parse().ok()).unwrap_or(30)
}

/// Every frame ends with the cursor sequence, which makes it the frame separator.
const FRAME_END: &str = "\x1b[?25h";

fn dup(fd: RawFd) -> File {
  unsafe { File::from_raw_fd(libc::dup(fd)) }
}

/// `(master, slave)`: two raw fds; the caller owns both.
fn open_pty() -> (RawFd, RawFd) {
  let mut master: RawFd = -1;
  let mut slave: RawFd = -1;
  let mut size = libc::winsize { ws_row: rows(), ws_col: cols(), ws_xpixel: 0, ws_ypixel: 0 };
  let opened =
    unsafe { libc::openpty(&mut master, &mut slave, std::ptr::null_mut(), std::ptr::null_mut(), &mut size) };
  assert_eq!(opened, 0, "openpty: {}", std::io::Error::last_os_error());
  (master, slave)
}

struct Terminal {
  child: Child,
  keys: File,
  seen: Arc<Mutex<Vec<u8>>>,
}

impl Terminal {
  fn spawn() -> Terminal {
    Terminal::spawn_with(&[])
  }

  /// `env` overrides the child's environment; VISUR_WS is how a test aims the client at a fake server.
  fn spawn_with(env: &[(&str, String)]) -> Terminal {
    let (master, slave) = open_pty();
    // VISUR_WRAPPER lets the same check drive ./visur instead of the client, which is how the launcher's
    // cold-start path gets tested: same keys, same assertions, one more process in between.
    let program =
      std::env::var("VISUR_WRAPPER").unwrap_or_else(|_| env!("CARGO_BIN_EXE_visur-term").to_string());
    let child = Command::new(program)
      .envs(env.iter().map(|(key, value)| (*key, value.as_str())))
      .stdin(Stdio::from(dup(slave)))
      .stdout(Stdio::from(dup(slave)))
      .stderr(Stdio::from(dup(slave)))
      .spawn()
      .expect("could not start the client");
    unsafe { libc::close(slave) };

    let keys = dup(master);
    let seen = Arc::new(Mutex::new(Vec::new()));
    let sink = Arc::clone(&seen);
    std::thread::spawn(move || {
      // Non-blocking reads: the pty master has no data most of the time, and the test thread is waiting on it.
      let flags = unsafe { libc::fcntl(master, libc::F_GETFL) };
      unsafe { libc::fcntl(master, libc::F_SETFL, flags | libc::O_NONBLOCK) };
      let mut from_master = unsafe { File::from_raw_fd(master) };
      let mut chunk = [0u8; 8192];
      loop {
        match from_master.read(&mut chunk) {
          Ok(0) => return,
          Ok(count) => sink.lock().unwrap().extend_from_slice(&chunk[..count]),
          Err(_) => std::thread::sleep(Duration::from_millis(1)),
        }
      }
    });
    Terminal { child, keys, seen }
  }

  fn raw(&self) -> String {
    String::from_utf8_lossy(&self.seen.lock().unwrap()).to_string()
  }

  fn frames(&self) -> usize {
    self.raw().matches(FRAME_END).count()
  }

  /// The most recent *complete* frame, with the escape sequences removed: what a person would see.
  /// Empty until a frame has finished, so an assertion never reads a half-written screen.
  /// The screen as a terminal would show it: every frame replayed in order, so a diff paints onto what is already
  /// there. Reading only the last frame was right while every frame was a full repaint, and became wrong the moment
  /// the client learned to send damage.
  fn screen(&self) -> String {
    const WIDTH: usize = 320;
    const HEIGHT: usize = 90;
    let mut cells = vec![vec![' '; WIDTH]; HEIGHT];
    let (mut row, mut col) = (0, 0);
    let raw = self.raw();
    let mut text = raw.chars().peekable();
    while let Some(ch) = text.next() {
      if ch != '\u{1b}' {
        if ch == '\n' {
          row = (row + 1).min(HEIGHT - 1);
        } else if ch != '\r' {
          if row < HEIGHT && col < WIDTH {
            cells[row][col] = ch;
          }
          col += 1;
        }
        continue;
      }
      if text.next() != Some('[') {
        continue;
      }
      let mut params = String::new();
      let mut final_byte = None;
      for code in text.by_ref() {
        if ('@'..='~').contains(&code) {
          final_byte = Some(code);
          break;
        }
        params.push(code);
      }
      match final_byte {
        Some('H') => {
          let (r, c) = params.split_once(';').unwrap_or(("1", "1"));
          row = r.parse::<usize>().unwrap_or(1).saturating_sub(1);
          col = c.parse::<usize>().unwrap_or(1).saturating_sub(1);
        }
        // Erase to the end of the row, which is how the client clears a blank tail.
        Some('K') => {
          if row < HEIGHT {
            for cell in cells[row].iter_mut().skip(col) {
              *cell = ' ';
            }
          }
        }
        _ => {}
      }
    }
    cells.iter().map(|line| line.iter().collect::<String>()).collect::<Vec<_>>().join("\n")
  }

  /// Where the last frame left the cursor, one-based, straight from its final `CUP`.
  fn cursor(&self) -> Option<(usize, usize)> {
    let raw = self.raw();
    let last = raw.rsplit(FRAME_END).next().unwrap_or_default();
    last.match_indices("\x1b[")
      .filter_map(|(at, _)| {
        let rest = &last[at + 2..];
        let end = rest.find(['H', 'q'])?;
        if rest.as_bytes().get(end) != Some(&b'H') {
          return None;
        }
        let (row, col) = rest[..end].split_once(';')?;
        Some((row.parse().ok()?, col.parse().ok()?))
      })
      .last()
  }

  fn send(&mut self, bytes: &str) {
    let _ = self.keys.write_all(bytes.as_bytes());
    let _ = self.keys.flush();
  }

  /// Every complete frame, in order. A frame ends with the cursor sequence, so that is the separator.
  fn frames_raw(&self) -> Vec<String> {
    let raw = String::from_utf8_lossy(self.raw().as_bytes()).to_string();
    let mut frames = Vec::new();
    let mut rest = raw.as_str();
    while let Some(end) = rest.find(FRAME_END) {
      frames.push(rest[..end + FRAME_END.len()].to_string());
      rest = &rest[end + FRAME_END.len()..];
    }
    frames
  }

  /// How many rows the last complete frame addressed: the height the client actually painted at.
  fn frame_rows(&self) -> usize {
    let raw = self.raw();
    let Some(end) = raw.rfind(FRAME_END) else { return 0 };
    let before = &raw[..end];
    let start = before.rfind(FRAME_END).map_or(0, |at| at + FRAME_END.len());
    let mut rows = 0;
    for (at, _) in raw[start..end].match_indices("\x1b[") {
      let rest = &raw[start + at + 2..];
      let digits = rest.chars().take_while(|c| c.is_ascii_digit()).collect::<String>();
      if digits.is_empty() || !rest[digits.len()..].starts_with(';') {
        continue;
      }
      if let Ok(row) = digits.parse::<usize>() {
        rows = rows.max(row);
      }
    }
    rows
  }

  /// Send a key and return how long the resulting frame took, in milliseconds.
  fn press(&mut self, bytes: &str) -> Option<f64> {
    let before = self.frames();
    let start = Instant::now();
    self.send(bytes);
    for _ in 0..1000 {
      if self.frames() > before {
        return Some(start.elapsed().as_secs_f64() * 1000.0);
      }
      std::thread::sleep(Duration::from_micros(200));
    }
    None
  }

  fn wait_for(&self, timeout: Duration, done: impl Fn(&Terminal) -> bool) -> bool {
    let deadline = Instant::now() + timeout;
    while Instant::now() < deadline {
      if done(self) {
        return true;
      }
      std::thread::sleep(Duration::from_millis(5));
    }
    done(self)
  }

  fn wait_for_screen(&self, text: &str) -> bool {
    self.wait_for(Duration::from_secs(5), |terminal| terminal.screen().contains(text))
  }

  /// Resize the terminal the way a window manager does, and tell the client, which is not this pty's session leader
  /// and so does not receive the ioctl's own SIGWINCH.
  fn resize(&mut self, cols: u16, rows: u16) {
    let size = libc::winsize { ws_row: rows, ws_col: cols, ws_xpixel: 0, ws_ypixel: 0 };
    let set = unsafe { libc::ioctl(self.keys.as_raw_fd(), libc::TIOCSWINSZ, &size) };
    assert_eq!(set, 0, "TIOCSWINSZ: {}", std::io::Error::last_os_error());
    unsafe { libc::kill(self.child.id() as i32, libc::SIGWINCH) };
  }

  /// Wait for the client to leave, but never forever: a client that will not exit is a failure, not a hang.
  fn wait_exit(&mut self, timeout: Duration) -> ExitStatus {
    let deadline = Instant::now() + timeout;
    while Instant::now() < deadline {
      if let Some(status) = self.child.try_wait().expect("waiting for the client") {
        return status;
      }
      std::thread::sleep(Duration::from_millis(20));
    }
    panic!("the client did not exit within {timeout:?}; screen: {:?}", self.screen())
  }

  fn quit(mut self) -> ExitStatus {
    self.wait_exit(Duration::from_secs(5))
  }
}

impl Drop for Terminal {
  /// A failed assertion must not leave a client attached: the server keeps the viewport in one global slot, and a
  /// stray socket is somebody else's confusing failure later.
  fn drop(&mut self) {
    if let Ok(path) = std::env::var("VISUR_DUMP") {
      std::fs::write(path, self.seen.lock().unwrap().as_slice()).ok();
    }
    let _ = self.child.kill();
    let _ = self.child.wait();
  }
}

/// Drop the escape sequences so the result is what a person would read.

fn server_is_up() -> bool {
  let port = std::env::var("VISUR_PORT").ok().and_then(|value| value.parse().ok()).unwrap_or(8888);
  std::net::TcpStream::connect(("127.0.0.1", port)).is_ok()
}

/// What a fake server does once the handshake is done.
enum Fake {
  /// Hang up at once: the server died under a client that was already attached.
  HangUp,
  /// Stay and record what the client sends.
  Listen,
  /// Answer each message the client sends with the next view in the list, then keep listening.
  Script(Vec<String>),
}

/// A WebSocket server on an ephemeral port that returns `(port, frames the client sent)`. Not the real server: these
/// checks are about what the client does on its own, so they need no JVM and no `VISUR_E2E`.
fn fake_server(behaviour: Fake) -> (u16, Arc<Mutex<Vec<String>>>) {
  let listener = std::net::TcpListener::bind(("127.0.0.1", 0)).expect("bind a fake server");
  let port = listener.local_addr().unwrap().port();
  let frames = Arc::new(Mutex::new(Vec::new()));
  let sink = Arc::clone(&frames);
  std::thread::spawn(move || {
    let Ok((stream, _)) = listener.accept() else { return };
    let Ok(mut socket) = tungstenite::accept(stream) else { return };
    let mut script = match behaviour {
      Fake::HangUp => {
        let _ = socket.close(None);
        return;
      }
      Fake::Listen => Vec::new(),
      Fake::Script(views) => views,
    };
    while let Ok(message) = socket.read() {
      if let tungstenite::Message::Text(text) = message {
        sink.lock().unwrap().push(text.to_string());
        if !script.is_empty() {
          let _ = socket.send(tungstenite::Message::Text(script.remove(0).into()));
        }
      }
    }
  });
  (port, frames)
}

/// Endpoint for a fake server, in the form the client takes from VISUR_WS.
fn ws_to(port: u16) -> String {
  format!("ws://127.0.0.1:{port}/ws")
}

/// A `viewWasChanged` frame with one row of text, in the shape the client parses.
fn view(text: &str, full: bool) -> String {
  format!(
    r#"{{"address":"viewWasChanged","body":{{"full":{full},"origin":0,"rows":[{{"start":0,"text":"{text}"}}],"span":0,"cursorRow":0,"cursorCol":0,"cursorQuantumStart":0,"cursorQuantumEnd":0,"isAtQuantumStart":true,"cursorQuantum":"character","scopeQuantum":"character","editorMode":"navigate","editorSubmode":"navigate","isInCommandState":false,"commandStateContent":"","commandCursor":0,"searchTarget":""}}}}"#
  )
}

fn command_view(content: &str, full: bool) -> String {
  view(content, full)
    .replace("\"isInCommandState\":false", "\"isInCommandState\":true")
    .replace("\"commandStateContent\":\"\"", &format!("\"commandStateContent\":\"{content}\""))
    .replace("\"commandCursor\":0", &format!("\"commandCursor\":{}", content.len()))
}

#[test]
fn semicolon_command_line_is_painted_and_escape_returns_to_navigation() {
  let (port, sent) = fake_server(Fake::Script(vec![
    view("", true),
    command_view("", false),
    command_view("x", false),
    view("", false),
  ]));
  let mut terminal = Terminal::spawn_with(&[("VISUR_WS", ws_to(port))]);
  assert!(terminal.wait_for(Duration::from_secs(5), |t| t.frames() >= 1), "initial view was not painted");

  assert!(terminal.press(";").is_some(), "semicolon did not produce a view");
  assert!(terminal.press("x").is_some(), "typed text did not produce a view");
  assert!(terminal.screen().contains(";x"), "command row was not painted: {:?}", terminal.screen());
  assert!(terminal.press("\x1b").is_some(), "Escape did not produce a view");

  let messages = sent.lock().unwrap().join("\n");
  assert!(messages.contains("\"key\":\";\""), "semicolon was not sent: {messages}");
  assert!(messages.contains("\"key\":\"x\""), "text was not sent: {messages}");
  assert!(messages.contains("\"key\":\"Escape\""), "Escape was not sent: {messages}");
}

#[test]
fn bracketed_multiline_paste_is_one_wire_message_and_paints_the_reply() {
  let reply = serde_json::json!({
    "address": "viewWasChanged",
    "body": {
      "full": false, "origin": 0,
      "rows": [{"start": 0, "text": "first"}, {"start": 6, "text": "second"}],
      "span": 0, "cursorRow": 1, "cursorCol": 5,
      "cursorQuantumStart": 12, "cursorQuantumEnd": 13, "isAtQuantumStart": true,
      "cursorQuantum": "character", "scopeQuantum": "character",
      "editorMode": "navigate", "editorSubmode": "navigate", "isInCommandState": false,
      "commandStateContent": "", "commandCursor": 0, "searchTarget": ""
    }
  }).to_string();
  let (port, sent) = fake_server(Fake::Script(vec![view("", true), reply]));
  let mut terminal = Terminal::spawn_with(&[("VISUR_WS", ws_to(port))]);
  assert!(terminal.wait_for(Duration::from_secs(5), |t| t.frames() >= 1), "initial view was not painted");

  terminal.send("\x1b[200~first\r\nsecond\x1b[201~");
  assert!(terminal.wait_for(Duration::from_secs(5), |t| t.frames() >= 2), "paste reply was not painted");
  assert!(terminal.screen().contains("first") && terminal.screen().contains("second"), "multiline reply not painted: {:?}", terminal.screen());

  let messages = sent.lock().unwrap();
  let paste_messages = messages.iter().filter(|frame| {
    serde_json::from_str::<serde_json::Value>(frame).is_ok_and(|value| value["address"] == "pasteWasReceived")
  }).collect::<Vec<_>>();
  assert_eq!(paste_messages.len(), 1, "paste must be one request, not one key per character: {messages:?}");
  let payload: serde_json::Value = serde_json::from_str(paste_messages[0]).unwrap();
  assert_eq!(payload["body"]["text"], "first\r\nsecond");
}

#[test]
fn a_keystroke_paints_the_row_it_changed_not_the_screen() {
  let (port, _sent) = fake_server(Fake::Script(vec![view("hello", true), view("hellp", false)]));
  let mut terminal = Terminal::spawn_with(&[("VISUR_WS", ws_to(port))]);

  assert!(
    terminal.wait_for(Duration::from_secs(5), |t| t.frames() >= 1),
    "the first view never got painted"
  );
  // After the first paint, not before: a key typed inside the 60 ms capability probe is consumed with its answer.
  terminal.send("x");
  assert!(
    terminal.wait_for(Duration::from_secs(5), |t| t.frames() >= 2),
    "the client painted only {} frame(s)",
    terminal.frames()
  );

  let frames = terminal.frames_raw();
  let (first, second) = (&frames[0], &frames[1]);
  assert!(first.contains("\x1b[2;1H"), "the first frame is a full screen");
  assert!(
    second.len() * 4 < first.len(),
    "a keystroke must paint the change, not the screen: {} bytes after the edit, {} for the full frame",
    second.len(),
    first.len()
  );
  assert!(second.contains("\x1b[1;5H"), "the changed row is re-sent from the column that changed: {second:?}");
  assert!(!second.contains("\x1b[2;1H"), "the row that did not change is not re-sent: {second:?}");
}

#[test]
fn a_resize_is_declared_again_so_the_server_resends_the_view() {
  let (port, sent) = fake_server(Fake::Listen);
  let mut terminal = Terminal::spawn_with(&[("VISUR_WS", ws_to(port))]);

  assert!(
    terminal.wait_for(Duration::from_secs(5), |_| declared(&sent, cols() as usize, rows() as usize - 1)),
    "the client did not declare its viewport at attach: {:?}",
    sent.lock().unwrap()
  );

  terminal.resize(120, 40);

  // Content rows, not terminal rows: the last row is the client's own mode line.
  assert!(
    terminal.wait_for(Duration::from_secs(5), |_| declared(&sent, 120, 39)),
    "a resize must be declared again: {:?}",
    sent.lock().unwrap()
  );

  // The rows painted before the resize are the wrong shape now, and a stale screen is exactly what leaves old text in
  // rows the new height does not cover. The next frame must address every row of the new grid.
  assert!(
    terminal.wait_for(Duration::from_secs(2), |t| t.frame_rows() == 40),
    "after resizing to 40 rows the client painted {} rows",
    terminal.frame_rows()
  );
  let (row, col) = terminal.cursor().expect("a cursor position after the resize");
  assert!(row <= 40, "the cursor is outside the 40-row grid at row {row}");
  assert!(col <= 120, "the cursor is outside the 120-column grid at column {col}");
}

#[test]
fn the_client_probes_synchronized_output_instead_of_assuming_it() {
  let (port, _) = fake_server(Fake::Listen);
  let mut terminal = Terminal::spawn_with(&[("VISUR_WS", ws_to(port))]);

  assert!(
    terminal.wait_for(Duration::from_secs(2), |t| t.raw().contains("\x1b[?2026$p")),
    "the client must ask whether the terminal has synchronized output: {:?}",
    terminal.raw()
  );
  assert!(
    terminal.wait_for(Duration::from_secs(2), |t| t.frames() > 0),
    "the client did not paint: {:?}",
    terminal.raw()
  );
  assert!(
    !terminal.raw().contains("\x1b[?2026h"),
    "nothing answered the probe, so the client must not use synchronized output"
  );
  assert!(
    terminal.raw().contains("\x1b[>1u"),
    "kitty's unambiguous-escape push belongs at attach: {:?}",
    terminal.raw()
  );

  terminal.send("Z");
  terminal.send("Q");
  let status = terminal.wait_exit(Duration::from_secs(5));
  assert_eq!(status.code(), Some(0), "ZQ detaches");
  let raw = terminal.raw();
  let popped = raw.rfind("\x1b[<u").expect("the keyboard flags pushed at attach must be popped on the way out");
  let left = raw.rfind("\x1b[?1049l").expect("the alternate screen must be left");
  assert!(popped < left, "pop the flags before leaving the alternate screen, per spec §5.5");
}

#[test]
fn synchronized_output_wraps_frames_when_the_terminal_says_yes() {
  let (port, _) = fake_server(Fake::Listen);
  let mut terminal = Terminal::spawn_with(&[("VISUR_WS", ws_to(port))]);

  assert!(
    terminal.wait_for(Duration::from_secs(2), |t| t.raw().contains("\x1b[?2026$p")),
    "the probe must come before any frame, or there is nothing to gate"
  );
  terminal.send("\x1b[?2026;1$y"); // the terminal answering "set"

  assert!(
    terminal.wait_for(Duration::from_secs(2), |t| t.raw().contains("\x1b[?2026h")),
    "an answered probe must wrap the frame in synchronized output: {:?}",
    terminal.raw()
  );
}

/// Did the client send a viewport declaration of exactly this size?
fn declared(frames: &Arc<Mutex<Vec<String>>>, cols: usize, rows: usize) -> bool {
  frames.lock().unwrap().iter().any(|frame| {
    let Ok(value) = serde_json::from_str::<serde_json::Value>(frame) else { return false };
    value["address"] == "viewportWasChanged"
      && value["body"]["cols"] == cols
      && value["body"]["rows"] == rows
  })
}

#[test]
fn the_client_leaves_and_restores_the_terminal_when_the_server_dies() {
  let (port, _) = fake_server(Fake::HangUp);
  let mut terminal = Terminal::spawn_with(&[("VISUR_WS", ws_to(port))]);

  // The socket is already closed, so nothing will ever arrive: the client has to notice, not wait for a view.
  let status = terminal.wait_exit(Duration::from_secs(3));
  assert!(
    !matches!(status.code(), Some(0) | Some(3)),
    "a client whose server vanished must not report detach or quit: {status:?}"
  );

  // Restored, in order: the cursor back on, then the alternate screen gone, which hands the shell back.
  let raw = terminal.raw();
  let shown = raw.rfind("\x1b[?25h").expect("the cursor was never made visible again");
  let left = raw.rfind("\x1b[?1049l").expect("the alternate screen was never left");
  assert!(shown < left, "the cursor must be restored before the alternate screen is left");
  assert!(
    raw.len() - left < 64,
    "nothing should be painted after the terminal is handed back: {:?}",
    &raw[left..]
  );
}

/// The case the conformance ticket names as untried: a resize arrives while a search submode is live. The submode is
/// editor state on the server and the geometry is the client's, so the two have to survive each other.
#[test]
fn a_resize_during_a_search_submode_keeps_the_search_and_repaints() {
  if std::env::var("VISUR_E2E").is_err() {
    eprintln!("skipped: set VISUR_E2E=1 with a server running to run the resize-during-search check");
    return;
  }
  let mut terminal = Terminal::spawn();
  assert!(
    terminal.wait_for(Duration::from_secs(5), |t| t.screen().chars().filter(|c| *c != ' ').count() > 500),
    "the first paint did not show a document"
  );

  // `f` starts a forward search; the submode is what the mode line names.
  terminal.press("\u{1b}");
  terminal.press("f");
  assert!(
    terminal.wait_for(Duration::from_secs(2), |t| t.screen().contains("(f)")),
    "the search submode never appeared: {:?}",
    terminal.screen()
  );

  terminal.resize(120, 40);

  // Still searching, and painted at the new height: 40 rows addressed, mode line on the last of them.
  assert!(
    terminal.wait_for(Duration::from_secs(5), |t| t.screen().contains("(f)") && t.frame_rows() == 40),
    "the search was lost by the resize, or the screen was not repainted at 40 rows: rows={} {:?}",
    terminal.frame_rows(),
    terminal.screen()
  );
  let (row, col) = terminal.cursor().expect("a cursor position after the resize");
  assert!(row <= 40 && col <= 120, "the cursor left the resized grid at {row},{col}");

  terminal.send("Z");
  terminal.send("Q");
  assert_eq!(terminal.quit().code(), Some(0), "ZQ should detach");
}

#[test]
fn live_multiline_paste_and_document_jumps() {
  if std::env::var("VISUR_E2E").is_err() {
    eprintln!("skipped: set VISUR_E2E=1 with a server running to run paste and jump checks");
    return;
  }
  // Skipped unless a server is up, which is how a stale key sequence survives a deletion unnoticed. These presses are
  // the grammar of the editor: `[ r` and `] r`, not the `gg` and `G` that were deleted, and `s` on its own, not a
  // `s` followed by the digit of the span submode that was deleted.
  assert!(server_is_up(), "no server on 127.0.0.1:8888 — start one with ./gradlew run");
  let mut terminal = Terminal::spawn();
  assert!(terminal.wait_for(Duration::from_secs(5), |t| t.screen().contains("MODE")), "initial view was not painted");
  terminal.press("\x1b");

  terminal.send("\x1b[200~FM_PASTE_FIRST\r\nFM_PASTE_SECOND\x1b[201~");
  assert!(terminal.wait_for(Duration::from_secs(5), |t| t.screen().contains("FM_PASTE_FIRST") && t.screen().contains("FM_PASTE_SECOND")), "server did not insert the multiline paste: {:?}", terminal.screen());

  terminal.press("[");
  terminal.press("r");
  let top = terminal.cursor().expect("cursor after [ r");
  terminal.press("]");
  terminal.press("r");
  let bottom = terminal.cursor().expect("cursor after ] r");
  assert!(bottom.0 > top.0, "] r should reach the bottom after [ r: {top:?} -> {bottom:?}");
  if std::env::var("VISUR_EXPECT_SHORT").is_ok() {
    assert!(bottom.0 < rows() as usize - 1, "short document bottom should stay above the viewport edge: {bottom:?}");
  }

  terminal.press("[");
  terminal.press("r");
  terminal.press("s");
  terminal.press("y");
  terminal.press("p");
  assert!(terminal.wait_for_screen("FFM_PASTE_FIRST"), "yank and paste-from-register did not duplicate the active span: {:?}", terminal.screen());

  terminal.send("Z");
  terminal.send("Q");
  assert_eq!(terminal.quit().code(), Some(0), "ZQ should detach");
}

#[test]
fn the_client_paints_edits_searches_and_detaches() {
  if std::env::var("VISUR_E2E").is_err() {
    eprintln!("skipped: set VISUR_E2E=1 with a server running to run the end-to-end check");
    return;
  }
  // The launcher is the thing that starts a server, so the precondition only applies when driving the client alone.
  if std::env::var("VISUR_WRAPPER").is_err() {
    assert!(server_is_up(), "no server on 127.0.0.1:8888 — start one with ./gradlew run");
  }

  let mut terminal = Terminal::spawn();

  // Attach: rows arrive without anyone typing. Asserted on shape rather than on the seed text, because the
  // document is shared with every other client of this server and earlier runs will have moved its cursor.
  assert!(
    terminal.wait_for(Duration::from_secs(5), |t| t.screen().chars().filter(|c| *c != ' ').count() > 500),
    "the first paint did not show a document: {:?}",
    terminal.screen()
  );
  // The document is shared with every earlier run, which may have left the editor in insert mode.
  terminal.press("\u{1b}");
  assert!(
    terminal.wait_for(Duration::from_secs(2), |t| t.screen().contains("MODE")),
    "no mode line was painted: {:?}",
    terminal.screen()
  );

  // Typing is the thing with a latency bar; sample the cheapest key.
  let samples: Vec<f64> = (0..10).filter_map(|_| terminal.press("j")).collect();
  let mut sorted = samples.clone();
  sorted.sort_by(f64::total_cmp);
  assert_eq!(samples.len(), 10, "every keystroke must produce a frame");
  println!("keystroke -> paint: median {:.2} ms over {} samples", sorted[sorted.len() / 2], sorted.len());

  // Cursor movement, both directions.
  let low = terminal.cursor().expect("a cursor position");
  assert_eq!(terminal.press("k").is_some(), true);
  let high = terminal.cursor().expect("a cursor position");
  assert!(high.0 < low.0 || high.1 < low.1, "k should move the cursor back: {low:?} -> {high:?}");

  // Insert text, then delete it again.
  for key in ["i", "x", "y", "z"] {
    terminal.press(key);
  }
  assert!(terminal.wait_for_screen("xyz"), "typed text did not appear: {:?}", terminal.screen());
  // Backspace is bound in insert and replace only, as in vim, so the deletion happens before leaving insert.
  for _ in 0..3 {
    terminal.press("\x7f");
  }
  assert!(
    terminal.wait_for(Duration::from_secs(2), |t| !t.screen().contains("xyz")),
    "backspace did not delete the text: {:?}",
    terminal.screen()
  );
  terminal.press("\x1b");
  assert!(terminal.wait_for_screen("NAVIGATE MODE"), "escape did not leave insert: {:?}", terminal.screen());

  // A newline splits the row, so the mode line stays on the last row and the cursor stays inside the viewport.
  terminal.press("i");
  terminal.press("\r");
  terminal.press("\x1b");
  let (row, _) = terminal.cursor().expect("a cursor position");
  assert!(row < rows() as usize, "the cursor left the grid at row {row}");

  // The count, the span it produced, the fence and the search all reach the mode line, which is how a terminal user
  // sees editor state at all. Each one is a trip through the model and back on the wire.
  terminal.press("3");
  assert!(
    terminal.wait_for(Duration::from_secs(2), |t| t.screen().contains("COUNT=3")),
    "a typed count never reached the mode line: {:?}",
    terminal.screen()
  );
  terminal.press("s");
  assert!(
    terminal.wait_for(Duration::from_secs(2), |t| t.screen().contains("SPAN=3")),
    "mode line lost its span: {:?}",
    terminal.screen()
  );
  assert!(
    terminal.wait_for(Duration::from_secs(2), |t| t.screen().contains("SCOPE=")),
    "mode line never named the fence: {:?}",
    terminal.screen()
  );
  assert!(
    !terminal.screen().contains("COUNT="),
    "a spent count outstayed its key: {:?}",
    terminal.screen()
  );
  terminal.press("f");
  assert!(
    terminal.wait_for(Duration::from_secs(2), |t| t.screen().contains("(f)")),
    "search did not reach the mode line: {:?}",
    terminal.screen()
  );
  terminal.press("e");
  terminal.press("\x1b");

  // ZQ detaches: the client exits 0 and leaves the server alone.
  terminal.send("Z");
  terminal.send("Q");
  let status = terminal.quit();
  assert_eq!(status.code(), Some(0), "ZQ should detach with exit code 0, got {status:?}");
}
