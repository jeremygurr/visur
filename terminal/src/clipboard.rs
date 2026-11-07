// The clipboard, seen from the machine that has one. The server never touches an OS clipboard: it asks, and this module
// is the asking translated into what a terminal understands — OSC 52, the only thing that works over SSH.
//
// Two things are true about that and this module is built around both. Writing works on the mainstream emulators;
// reading mostly does not — Terminal.app has no OSC 52 read, iTerm2 gates it behind a preference, and tmux swallows it.
// And a reply cannot be read by crossterm's event reader, which would take it as noise: every read here brackets its
// own raw read of fd 0 around the sequence it just sent, which is the same blind spot the capability probe has (see
// `probe_sync_output`) and the same reason visur's own terminal emulator is the endgame.

/// The answer to the write is nothing at all: an emulator that refused, truncated, or has no clipboard says so with
/// silence, so a write is fire-and-forget and the server's memory of it is the only record there is.
pub fn write(text: &str) -> Vec<u8> {
  format!("\x1b]52;c;{}\x07", base64::encode(text.as_bytes())).into_bytes()
}

/// Ask the clipboard for its contents. `?` as the payload is the query form: it says "answer", not "set".
pub fn read_request() -> Vec<u8> {
  b"\x1b]52;c;?\x07".to_vec()
}

/// Ask whether this terminal will let an application read the clipboard at all, and whether it will warn the user
/// first. iTerm2's "ask" mode answers here, which is the only way to find out without stealing a dialog mid-keystroke.
pub fn paste_control_query() -> Vec<u8> {
  b"\x1b]52;?;M\x07".to_vec()
}

/// How long the terminal gets to answer the permission query. Short, because a terminal that knows the query answers in
/// a few milliseconds and one that does not answers never; the whole of a clipboard read, both questions together, is
/// under the server's patience for it (`ClipboardContents.PATIENCE_IN_MILLIS`).
pub const PERMISSION_PATIENCE: std::time::Duration = std::time::Duration::from_millis(120);

/// How long the terminal gets to send the clipboard's contents: long enough for a big selection over a slow link, short
/// enough that a user who pressed `p` is not held up for a second and a half.
pub const REPLY_PATIENCE: std::time::Duration = std::time::Duration::from_millis(300);

/// What a terminal said about permission to read the clipboard.
#[derive(Debug, PartialEq, Eq)]
pub enum Permission {
  Allowed,
  Denied,
  /// Silence, or an answer that means nothing: the read is attempted anyway, because an emulator that does not know the
  /// query can still answer the query that matters.
  Unknown,
}

pub fn paste_control(reply: &str) -> Permission {
  let Some(at) = reply.rfind("]52;?") else { return Permission::Unknown };
  // Where the code sits in the answer is not settled between the emulators: iTerm2 answers `?;h;M`, xterm answers
  // `?;M` followed by the code. So this reads forward past the parts that are not the code and takes the first one
  // that means something, and gives up on a byte that could not be part of an answer.
  for character in reply[at + "]52;?".len()..].chars() {
    match character {
      // `n` is "do not allow", the one answer that means asking further is a waste of the user's keystrokes.
      'n' => return Permission::Denied,
      // `h` allowed, `m` allowed with a warning, `p` allowed and remembered, `l` the retired "low".
      'h' | 'm' | 'p' | 'l' => return Permission::Allowed,
      ';' | 'M' | '0'..='9' => continue,
      _ => return Permission::Unknown,
    }
  }
  Permission::Unknown
}

/// The clipboard contents a terminal sent, if the bytes it sent contain them. Terminated by BEL or by ST, because
/// emulators do both, and the answer is base64 of the clipboard's UTF-8 bytes.
pub fn reply_text(reply: &[u8]) -> Option<String> {
  // The last sequence in the bytes wins: whatever else arrived on the way, the newest answer is the one the terminal
  // just sent, and a reply found in an older one is the stale-paste bug wearing a parser.
  let at = reply.windows(4).rposition(|candidate| candidate == b"]52;")?;
  let after = &reply[at + 4..];
  const BEL: u8 = 7;
  let mut payload = Vec::new();
  let mut terminated = false;
  for index in 0..after.len() {
    let byte = after[index];
    if byte == BEL {
      terminated = true;
      break;
    }
    if byte == 0x1b {
      // `ESC \` is ST, the other terminator. Any other ESC is a different sequence starting, which means this one
      // was never finished, and half a reply is not a clipboard.
      terminated = after.get(index + 1) == Some(&b'\\');
      break;
    }
    payload.push(byte);
  }
  if !terminated {
    return None;
  }
  let separator = payload.iter().position(|byte| *byte == b';')?;
  let text = base64::decode(&String::from_utf8_lossy(&payload[separator + 1..]))?;
  // Not lossy: text that is not UTF-8 is not text, and pasting what the replacement character turns it into would be
  // inventing characters in a document the user is about to save.
  String::from_utf8(text).ok()
}

#[cfg(test)]
mod tests {
  use super::*;

  // The exact bytes, asserted rather than sampled: an OSC 52 sequence that is one character wrong is not a clipboard
  // write that failed loudly, it is a clipboard write that never happened, which is precisely how `\` once shipped.

  #[test]
  fn a_write_is_one_sequence_naming_the_clipboard_and_base64ing_the_text() {
    assert_eq!(
      String::from_utf8(write("one ")).unwrap(),
      "\x1b]52;c;b25lIA==\x07",
      "`c` is the clipboard buffer: xterm maps it to CLIPBOARD and iTerm2 documents it, where the cut buffers 0-7 are \
       a DEC thing no emulator pretends to have anymore"
    );
  }

  #[test]
  fn the_write_carries_the_utf8_bytes_and_not_the_characters() {
    // "é" is two bytes, and the two bytes are what the emulator will decode back.
    assert_eq!(String::from_utf8(write("é")).unwrap(), "\x1b]52;c;w6k=\x07");
  }

  #[test]
  fn a_write_of_nothing_is_still_a_write() {
    // An empty clipboard is a thing the user can put there, and visur can displace nothing but a whole selection.
    assert_eq!(String::from_utf8(write("")).unwrap(), "\x1b]52;c;\x07");
  }

  #[test]
  fn the_read_asks_and_the_query_asks_about_asking() {
    assert_eq!(String::from_utf8(read_request()).unwrap(), "\x1b]52;c;?\x07");
    assert_eq!(String::from_utf8(paste_control_query()).unwrap(), "\x1b]52;?;M\x07");
  }

  #[test]
  fn base64_matches_rfc_4648() {
    assert_eq!(base64::encode(b""), "");
    assert_eq!(base64::encode(b"f"), "Zg==");
    assert_eq!(base64::encode(b"fo"), "Zm8=");
    assert_eq!(base64::encode(b"foo"), "Zm9v");
    assert_eq!(base64::encode(b"foobar"), "Zm9vYmFy");
  }

  #[test]
  fn base64_decodes_what_it_encoded_including_every_alphabet_character() {
    let all = ALPHABET.as_bytes();
    for text in ["", "f", "fo", "foo", "foobar", "one two three\n", "é § ñ \u{1f600}"] {
      let encoded = base64::encode(text.as_bytes());
      assert_eq!(base64::decode(&encoded).as_deref(), Some(text.as_bytes()), "{text} round trip");
    }
    assert_eq!(all.len(), 64, "the alphabet is 64 characters or the arithmetic is wrong");
  }

  /// A whole paragraph, encoded by the system's `base64` rather than by this file, and long enough to run past one
  /// line of the alphabet's output: hand-rolled base64 is exactly the kind of thing that is right about "foo" and
  /// wrong about a clipboard, and the wrong version arrives as text the user did not copy.
  #[test]
  fn base64_agrees_with_the_one_everybody_else_uses() {
    let paragraph = "the quick brown fox jumps over the lazy dog\n0123456789 !@#$%^\u{26}*()  \u{e9}\u{e9}\u{e9} \u{1f600}\n";
    let expected = "dGhlIHF1aWNrIGJyb3duIGZveCBqdW1wcyBvdmVyIHRoZSBsYXp5IGRvZwowMTIzNDU2Nzg5ICFAIyQlXiYqKCkgIMOpw6nDqSDwn5iACg==";
    assert_eq!(base64::encode(paragraph.as_bytes()), expected);
    assert_eq!(base64::decode(expected).as_deref(), Some(paragraph.as_bytes()));
    assert_eq!(reply_text(format!("\x1b]52;c;{expected}\x07").as_bytes()).as_deref(), Some(paragraph));
  }

  #[test]
  fn base64_refuses_what_is_not_base64() {
    assert_eq!(base64::decode("not!base64"), None, "a stray character is not a clipboard");
    assert_eq!(base64::decode("Z=m9"), None, "padding in the middle is not a clipboard");
    assert_eq!(base64::decode("Zm9v="), None, "a length base64 cannot mean is not a clipboard");
    assert_eq!(base64::decode("Zm9vYmFy===="), None);
  }

  #[test]
  fn a_paste_control_answer_is_read_wherever_the_emulator_put_it() {
    // iTerm2 answers `n` when clipboard access is switched off, `h` when it is allowed silently, `m` when it is
    // allowed with a warning, and `p` when the user has said "allow, and remember".
    assert_eq!(paste_control("\x1b]52;?;n;M\x07"), Permission::Denied);
    assert_eq!(paste_control("\x1b]52;?;M;n\x07"), Permission::Denied);
    assert_eq!(paste_control("\x1b]52;?;h;M\x07"), Permission::Allowed);
    assert_eq!(paste_control("\x1b]52;?;m;M\x07"), Permission::Allowed);
    assert_eq!(paste_control("\x1b]52;?;p;M\x07"), Permission::Allowed);
    assert_eq!(paste_control(""), Permission::Unknown, "silence is not permission, and it is not a refusal either");
    assert_eq!(paste_control("\x1b[?2026;1$y"), Permission::Unknown, "somebody else's reply is not this one");
  }

  #[test]
  fn a_clipboard_reply_is_found_behind_either_terminator() {
    let bel = b"\x1b]52;c;b25lIA==\x07";
    let st = b"\x1b]52;c;b25lIA==\x1b\\";
    assert_eq!(reply_text(bel).as_deref(), Some("one "));
    assert_eq!(reply_text(st).as_deref(), Some("one "));
    assert_eq!(
      reply_text(b"j\x1b]52;c;Zm9v\x07k").as_deref(),
      Some("foo"),
      "a keystroke typed while the terminal was answering arrives in the same bytes"
    );
  }

  #[test]
  fn an_empty_reply_is_an_empty_clipboard_and_not_a_failure() {
    assert_eq!(reply_text(b"\x1b]52;c;\x07").as_deref(), Some(""));
  }

  #[test]
  fn a_reply_that_is_not_utf8_is_no_reply() {
    // Better to say nothing than to paste mojibake into somebody's document: the server treats no answer as the
    // clipboard being unknowable, which is true of these bytes.
    assert_eq!(reply_text(b"\x1b]52;c;/w==\x07"), None, "0xff alone is not a character");
    assert_eq!(reply_text(b"\x1b]52;c;\xff\xff\xff\x07"), None);
  }

  #[test]
  fn a_reply_without_a_terminator_has_not_arrived_yet() {
    assert_eq!(reply_text(b"\x1b]52;c;b25lIA=="), None, "half a reply is the same as no reply");
  }

  #[test]
  fn somebody_elses_reply_is_not_the_clipboard() {
    assert_eq!(reply_text(b"\x1b[?2026;1$y\x07"), None);
  }
}

/// The alphabet base64 actually means here: standard, padded, and 64 characters long.
pub const ALPHABET: &str = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";

/// Base64 by hand, because the alternative is a dependency for thirty lines in a client whose whole job is bytes.
pub mod base64 {
  use super::ALPHABET;

  pub fn encode(bytes: &[u8]) -> String {
    let alphabet = ALPHABET.as_bytes();
    let mut out = String::with_capacity(bytes.len().div_ceil(3) * 4);
    for chunk in bytes.chunks(3) {
      let first = chunk[0];
      let second = chunk.get(1).copied().unwrap_or(0);
      let third = chunk.get(2).copied().unwrap_or(0);
      let bits = ((first as u32) << 16) | ((second as u32) << 8) | third as u32;
      let indices = [bits >> 18, (bits >> 12) & 63, (bits >> 6) & 63, bits & 63];
      // Two bytes in means three characters out and one of padding; one in means two and two.
      let written = match chunk.len() {
        1 => 2,
        2 => 3,
        _ => 4,
      };
      for index in &indices[..written] {
        out.push(alphabet[*index as usize] as char);
      }
      for _ in written..4 {
        out.push('=');
      }
    }
    out
  }

  /// Strict where it matters: a character outside the alphabet, or data after padding, is not a clipboard, and
  /// decoding something anyway is how a truncated sequence becomes text the user did not copy.
  pub fn decode(text: &str) -> Option<Vec<u8>> {
    let mut out = Vec::with_capacity(text.len() / 4 * 3);
    let mut bits: u32 = 0;
    let mut width: u32 = 0;
    let mut data = 0;
    let mut pads = 0;
    for character in text.chars() {
      if character.is_whitespace() {
        // Long payloads come back wrapped, and the emulator that wrapped one meant the bytes on both sides of it.
        continue;
      }
      if character == '=' {
        // Only ever at the end (the check below this one), and no more than two of them.
        pads += 1;
        if pads > 2 {
          return None;
        }
        continue;
      }
      if pads > 0 {
        return None;
      }
      data += 1;
      bits = (bits << 6) | ALPHABET.find(character)? as u32;
      width += 6;
      if width >= 8 {
        out.push((bits >> (width - 8)) as u8);
        width -= 8;
      }
    }
    // A leftover of 6 bits means a character was cut in half, which means the payload was truncated: better no
    // clipboard than the beginning of one.
    if width == 6 {
      return None;
    }
    // Padding has to make the four-character groups whole, which is what tells "Zm9v=" (four characters of data, so a
    // group already complete, and then a pad that means a character was cut) from "Zg==" (two characters and two pads,
    // which is one byte and the padding that says so).
    if pads > 0 && (data + pads) % 4 != 0 {
      return None;
    }
    Some(out)
  }
}
