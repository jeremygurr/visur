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
use std::os::unix::io::{FromRawFd, RawFd};
use std::process::{Child, Command, ExitStatus, Stdio};
use std::sync::{Arc, Mutex};
use std::time::{Duration, Instant};

const COLS: u16 = 100;
const ROWS: u16 = 30;

/// Every frame ends with the cursor sequence, which makes it the frame separator.
const FRAME_END: &str = "\x1b[?25h";

fn dup(fd: RawFd) -> File {
  unsafe { File::from_raw_fd(libc::dup(fd)) }
}

/// `(master, slave)`: two raw fds; the caller owns both.
fn open_pty() -> (RawFd, RawFd) {
  let mut master: RawFd = -1;
  let mut slave: RawFd = -1;
  let mut size = libc::winsize { ws_row: ROWS, ws_col: COLS, ws_xpixel: 0, ws_ypixel: 0 };
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
    let (master, slave) = open_pty();
    let child = Command::new(env!("CARGO_BIN_EXE_visur-term"))
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
  fn screen(&self) -> String {
    let raw = self.raw();
    let Some(end) = raw.rfind(FRAME_END) else { return String::new() };
    let before = &raw[..end];
    let start = before.rfind(FRAME_END).map_or(0, |at| at + FRAME_END.len());
    strip(&raw[start..end])
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

  /// Wait for the client to leave, but never forever: a client that will not exit is a failure, not a hang.
  fn quit(mut self) -> ExitStatus {
    let deadline = Instant::now() + Duration::from_secs(5);
    while Instant::now() < deadline {
      if let Some(status) = self.child.try_wait().expect("waiting for the client") {
        return status;
      }
      std::thread::sleep(Duration::from_millis(20));
    }
    panic!("the client did not exit within 5s; screen: {:?}", self.screen())
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
fn strip(bytes: &str) -> String {
  let mut out = String::with_capacity(bytes.len());
  let mut chars = bytes.chars();
  while let Some(ch) = chars.next() {
    if ch != '\u{1b}' {
      out.push(ch);
      continue;
    }
    if chars.next() != Some('[') {
      continue;
    }
    // CSI: parameters until a final byte in 0x40..=0x7e.
    for code in chars.by_ref() {
      if ('@'..='~').contains(&code) {
        break;
      }
    }
  }
  out
}

fn server_is_up() -> bool {
  std::net::TcpStream::connect(("127.0.0.1", 8888)).is_ok()
}

#[test]
fn the_client_paints_edits_searches_and_detaches() {
  if std::env::var("VISUR_E2E").is_err() {
    eprintln!("skipped: set VISUR_E2E=1 with a server running to run the end-to-end check");
    return;
  }
  assert!(server_is_up(), "no server on 127.0.0.1:8888 — start one with ./gradlew run");

  let mut terminal = Terminal::spawn();

  // Attach: rows arrive without anyone typing. Asserted on shape rather than on the seed text, because the
  // document is shared with every other client of this server and earlier runs will have moved its cursor.
  assert!(
    terminal.wait_for(Duration::from_secs(5), |t| t.screen().chars().filter(|c| *c != ' ').count() > 500),
    "the first paint did not show a document: {:?}",
    terminal.screen()
  );
  assert!(terminal.screen().contains("NAVIGATE MODE"), "mode line: {:?}", terminal.screen());

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
  assert!(row < ROWS as usize, "the cursor left the grid at row {row}");

  // Span and search reach the mode line, which is how a terminal user sees editor state at all.
  terminal.press("s");
  terminal.press("1");
  assert!(
    terminal.wait_for(Duration::from_secs(2), |t| t.screen().contains("SPAN=")),
    "mode line lost its span: {:?}",
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
