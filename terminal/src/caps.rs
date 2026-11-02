// Component 7's capability half: what the terminal can do is asked, not assumed (spec §5.5). Every answer degrades
// silently — a terminal that says nothing is simply a terminal without the feature.

/// DECRQM for synchronized output: `CSI ? 2026 $ p`. Answer is `CSI ? 2026 ; Ps $ y`.
pub const PROBE: &str = "\x1b[?2026$p";

/// kitty's DISAMBIGUATE_ESCAPE_CODES: `Esc` arrives self-delimiting instead of waiting out a timeout (spec §5.4).
pub const PUSH: &str = "\x1b[>1u";
pub const POP: &str = "\x1b[<u";

#[derive(Debug, PartialEq)]
pub enum Sync {
  Supported,
  Unsupported,
  /// No usable answer: silence, a truncated reply, or a mode number that is not this one.
  Unknown,
}

pub fn sync_output(reply: &str) -> Sync {
  let marker = "[?2026;";
  let Some(at) = reply.rfind(marker) else { return Sync::Unknown };
  let rest = &reply[at + marker.len()..];
  let digits = rest.chars().take_while(char::is_ascii_digit).collect::<String>();
  if !rest[digits.len()..].starts_with("$y") {
    return Sync::Unknown;
  }
  match digits.as_str() {
    "1" => Sync::Supported,  // set
    "2" => Sync::Unsupported, // reset
    _ => Sync::Unknown,      // 0 not recognized, 3 reserved, 4 permanently set
  }
}

#[cfg(test)]
mod tests {
  use super::sync_output;
  use super::Sync::*;

  #[test]
  fn a_terminal_that_says_2026_is_set_supports_it() {
    assert_eq!(sync_output("\x1b[?2026;1$y"), Supported);
  }

  #[test]
  fn a_terminal_that_says_2026_is_reset_does_not() {
    assert_eq!(sync_output("\x1b[?2026;2$y"), Unsupported);
  }

  #[test]
  fn not_recognized_and_reserved_values_are_no_answer() {
    assert_eq!(sync_output("\x1b[?2026;0$y"), Unknown, "0 is \"not recognized\"");
    assert_eq!(sync_output("\x1b[?2026;3$y"), Unknown, "3 is reserved, so it answers nothing");
  }

  #[test]
  fn an_answer_about_another_mode_is_not_an_answer_about_this_one() {
    assert_eq!(sync_output("\x1b[?2027;1$y"), Unknown);
  }

  #[test]
  fn silence_and_noise_are_no_answer() {
    assert_eq!(sync_output(""), Unknown);
    assert_eq!(sync_output("j"), Unknown, "a keystroke is not a capability");
    assert_eq!(sync_output("\x1b[?2026;1"), Unknown, "a truncated reply must not count");
  }
}
