// The `Z` prefix: `ZZ` save and detach, `ZQ` detach, `ZD` quit.
//
// Nothing in Visur quits (spec §5.9), so the terminal owns this — and it owns it as input state, not editor
// state: one visible key wide. `ZZ` and `ZQ` both leave the server running; only `ZD` asks for it to stop, which
// it says with an exit code the launcher reads (ticket 08). `ZZ`'s save has nothing to save until file I/O
// exists, so today it is `ZQ` with intent — bound now so it starts working the day saving does.

#[derive(Debug, Clone, Copy, PartialEq, Eq, Default)]
pub enum Pending {
  #[default]
  None,
  Z,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub enum Effect {
  Nothing,
  Forward(String),
  SaveAndDetach,
  Detach,
  Quit,
}

/// Resolve one key against the pending prefix, returning the new prefix and what to do about the key.
pub fn resolve(pending: Pending, key: &str) -> (Pending, Effect) {
  match (pending, key) {
    (Pending::None, "Z") => (Pending::Z, Effect::Nothing),
    (Pending::Z, "Z") => (Pending::None, Effect::SaveAndDetach),
    (Pending::Z, "Q") => (Pending::None, Effect::Detach),
    (Pending::Z, "D") => (Pending::None, Effect::Quit),
    (Pending::Z, _) => (Pending::None, Effect::Nothing),
    (Pending::None, other) => (Pending::None, Effect::Forward(other.to_string())),
  }
}

#[cfg(test)]
mod tests {
  use super::*;

  #[test]
  fn detaches_on_the_double_z_prefixes() {
    assert_eq!(resolve(Pending::None, "Z"), (Pending::Z, Effect::Nothing));
    assert_eq!(resolve(Pending::Z, "Z"), (Pending::None, Effect::SaveAndDetach));
    assert_eq!(resolve(Pending::Z, "Q"), (Pending::None, Effect::Detach));
    assert_eq!(resolve(Pending::Z, "D"), (Pending::None, Effect::Quit));
  }

  #[test]
  fn forwards_ordinary_keys_when_nothing_is_pending() {
    assert_eq!(resolve(Pending::None, "j"), (Pending::None, Effect::Forward("j".to_string())));
    assert_eq!(resolve(Pending::None, "Escape"), (Pending::None, Effect::Forward("Escape".to_string())));
  }

  #[test]
  fn swallows_a_z_without_quit_keys_after_it() {
    // vim behaviour: Z is a prefix command, so it never reaches the buffer.
    for other in ["j", "ArrowLeft", "Esc", "1"] {
      assert_eq!(resolve(Pending::Z, other), (Pending::None, Effect::Nothing), "{other}");
    }
  }
}
