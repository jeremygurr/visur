// Component 7 of the design, the restore half: the terminal is handed back the same way however the client leaves.
//
// crossterm does not restore on drop, so without a guard a panic leaves raw mode on and the alternate screen up —
// the shell is still there, it just no longer echoes or shows what you type. A `Drop` guard runs during unwinding,
// which is the only mechanism that covers every exit path (spec §5.5).

use crossterm::terminal;
use std::io::Write;

/// Ordered, as the spec requires: attributes, synchronized output off, the cursor, the keyboard flags, the alternate
/// screen, and raw mode last (spec §5.5). Assembled from `caps` so the pop can never drift from the push.
pub fn restore_sequence() -> String {
  ["\x1b[0m\x1b[?2026l\x1b[?25h", crate::caps::POP, "\x1b[?1049l"].concat()
}

pub struct Restore<W: Write> {
  out: W,
  done: bool,
}

impl<W: Write> Restore<W> {
  pub fn new(out: W) -> Restore<W> {
    Restore { out, done: false }
  }

  /// Called by `main` before `process::exit`, which skips destructors: the one exit path `Drop` cannot cover.
  /// Idempotent, so `Drop` after it neither writes twice nor fights it.
  pub fn now(&mut self) {
    if self.done {
      return;
    }
    self.done = true;
    let _ = self.out.write_all(restore_sequence().as_bytes());
    let _ = self.out.flush();
    let _ = terminal::disable_raw_mode();
  }
}

impl<W: Write> Drop for Restore<W> {
  fn drop(&mut self) {
    self.now();
  }
}

#[cfg(test)]
mod tests {
  use super::*;
  use std::cell::RefCell;
  use std::panic::{catch_unwind, AssertUnwindSafe};
  use std::rc::Rc;

  /// A writer the test can read after the guard is gone.
  #[derive(Clone, Default)]
  struct Shared(Rc<RefCell<Vec<u8>>>);

  impl Write for Shared {
    fn write(&mut self, bytes: &[u8]) -> std::io::Result<usize> {
      self.0.borrow_mut().extend_from_slice(bytes);
      Ok(bytes.len())
    }
    fn flush(&mut self) -> std::io::Result<()> {
      Ok(())
    }
  }

  fn written(shared: &Shared) -> String {
    String::from_utf8_lossy(&shared.0.borrow()).to_string()
  }

  #[test]
  fn dropping_the_guard_hands_the_terminal_back_in_order() {
    let shared = Shared::default();
    drop(Restore::new(shared.clone()));

    let text = written(&shared);
    let sync = text.find("\x1b[?2026l").expect("synchronized output must be turned off first");
    let cursor = text.find("\x1b[?25h").expect("the cursor must be made visible again");
    let flags = text.find("\x1b[<u").expect("the keyboard flags must be popped");
    let screen = text.find("\x1b[?1049l").expect("the alternate screen must be left");
    assert!(
      sync < cursor && cursor < flags && flags < screen,
      "spec §5.5 order is ?2026l, ?25h, pop flags, ?1049l: {text:?}"
    );
  }

  #[test]
  fn a_panic_still_hands_the_terminal_back() {
    let shared = Shared::default();
    let guard = Restore::new(shared.clone());

    let panicked = catch_unwind(AssertUnwindSafe(move || {
      let _restore = guard;
      panic!("the client fell over");
    }));

    assert!(panicked.is_err(), "the test needs the panic to have happened");
    let text = written(&shared);
    assert!(
      text.contains("\x1b[?1049l"),
      "a panicking client must not leave the alternate screen up: {text:?}"
    );
    assert!(
      text.contains("\x1b[?25h"),
      "a panicking client must not leave the cursor hidden: {text:?}"
    );
  }

  #[test]
  fn asking_now_writes_the_same_thing_once() {
    let shared = Shared::default();
    let mut guard = Restore::new(shared.clone());
    guard.now();
    assert_eq!(written(&shared), restore_sequence());
    drop(guard);
    assert_eq!(
      written(&shared),
      restore_sequence(),
      "exit before `Drop` must not write the sequence twice"
    );
  }
}
