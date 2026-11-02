// Component 2 of the design: crossterm `Event` -> Visur key token, and nothing else.
//
// Visur's whole vocabulary is seven names plus any single character (spec §5.4). A token outside that set
// misses every keymap and is dropped silently by the server, so the adapter's job is mostly to *refuse*.

use crossterm::event::{Event, KeyCode, KeyEventKind, KeyModifiers};

/// `KeyPressed` is a string; these are the seven fixed names it accepts.
const NAMED: [(KeyCode, &str); 7] = [
  (KeyCode::Esc, "Escape"),
  (KeyCode::Enter, "Enter"),
  (KeyCode::Backspace, "Backspace"),
  (KeyCode::Left, "ArrowLeft"),
  (KeyCode::Right, "ArrowRight"),
  (KeyCode::Up, "ArrowUp"),
  (KeyCode::Down, "ArrowDown"),
];

fn named(code: KeyCode) -> Option<&'static str> {
  NAMED.iter().find(|(c, _)| *c == code).map(|(_, name)| *name)
}

/// One token per key, or nothing.
pub fn action(event: Event) -> Option<String> {
  let Event::Key(key) = event else { return None }; // paste and resize are not keys
  if key.kind == KeyEventKind::Release {
    return None; // Visur has only ever seen keydown
  }
  if key
    .modifiers
    .intersects(KeyModifiers::CONTROL | KeyModifiers::ALT | KeyModifiers::SUPER | KeyModifiers::HYPER)
  {
    // Drop chords. Otherwise Ctrl-d arrives as Char('d') and fires navigate's d = deleteCursorQuantum.
    // SHIFT is deliberately not a chord: it is how an uppercase character arrives at all, and Visur's F, N and
    // Z-prefix bindings are uppercase. Dropping it makes them unreachable.
    return None;
  }
  let token = match key.code {
    KeyCode::Char(c) => c.to_string(),
    code => named(code)?.to_string(),
  };
  Some(token)
}

#[cfg(test)]
mod tests {
  use super::*;
  use crossterm::event::{KeyEvent, KeyModifiers};

  fn key(code: KeyCode) -> Event {
    Event::Key(KeyEvent::new(code, KeyModifiers::NONE))
  }

  fn token(code: KeyCode) -> Option<String> {
    action(key(code))
  }

  #[test]
  fn maps_the_seven_fixed_names() {
    for (code, name) in [
      (KeyCode::Esc, "Escape"),
      (KeyCode::Enter, "Enter"),
      (KeyCode::Backspace, "Backspace"),
      (KeyCode::Left, "ArrowLeft"),
      (KeyCode::Right, "ArrowRight"),
      (KeyCode::Up, "ArrowUp"),
      (KeyCode::Down, "ArrowDown"),
    ] {
      assert_eq!(token(code).as_deref(), Some(name), "{code:?}");
    }
  }

  #[test]
  fn maps_any_single_character_verbatim() {
    assert_eq!(token(KeyCode::Char('j')).as_deref(), Some("j"));
    assert_eq!(token(KeyCode::Char('F')).as_deref(), Some("F")); // shift needs no handling: it arrives uppercase
    assert_eq!(token(KeyCode::Char(' ')).as_deref(), Some(" "));
    assert_eq!(token(KeyCode::Char(';')).as_deref(), Some(";"));
  }

  #[test]
  fn drops_every_modifier_chord() {
    for mods in [
      KeyModifiers::CONTROL,
      KeyModifiers::ALT,
      KeyModifiers::SUPER,
      KeyModifiers::CONTROL | KeyModifiers::SHIFT,
    ] {
      let chord = Event::Key(KeyEvent::new(KeyCode::Char('d'), mods));
      assert_eq!(action(chord), None, "{mods:?}+d must not reach the keymap");
    }
  }

  #[test]
  fn keeps_shift_because_it_is_only_how_an_uppercase_character_arrives() {
    // Measured, not assumed: crossterm reports SHIFT alongside Char('Z') for the shift key. Dropping it would make
    // every uppercase binding -- F, N, and the Z prefix -- unreachable.
    let shift = Event::Key(KeyEvent::new(KeyCode::Char('Z'), KeyModifiers::SHIFT));
    assert_eq!(action(shift).as_deref(), Some("Z"));
  }

  #[test]
  fn drops_unbound_keys_rather_than_inventing_tokens() {
    for code in [
      KeyCode::Tab,
      KeyCode::Home,
      KeyCode::End,
      KeyCode::Insert,
      KeyCode::Delete,
      KeyCode::PageUp,
      KeyCode::PageDown,
      KeyCode::F(5),
    ] {
      assert_eq!(token(code), None, "{code:?}");
    }
  }

  #[test]
  fn drops_release_and_forwards_repeat() {
    let release = Event::Key(KeyEvent::new_with_kind(
      KeyCode::Char('j'),
      KeyModifiers::NONE,
      KeyEventKind::Release,
    ));
    assert_eq!(action(release), None);

    let repeat = Event::Key(KeyEvent::new_with_kind(
      KeyCode::Char('j'),
      KeyModifiers::NONE,
      KeyEventKind::Repeat,
    ));
    assert_eq!(action(repeat).as_deref(), Some("j"), "one token per auto-repeat");
  }

  #[test]
  fn drops_events_that_are_not_keys() {
    assert_eq!(action(Event::Paste("hello".to_string())), None);
    assert_eq!(action(Event::Resize(80, 24)), None);
    assert_eq!(action(Event::FocusGained), None);
  }
}
