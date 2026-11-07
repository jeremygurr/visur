// Component 9 of the design: the socket. Plain JSON to the server's `/ws`, frames being the bus envelope in both
// directions (spec §2.1) — the transport invents no vocabulary of its own.

use serde_json::{json, Value};
use std::net::TcpStream;
use std::time::Duration;
use tungstenite::stream::MaybeTlsStream;
use tungstenite::{connect as connect_ws, Message, WebSocket};

pub const URL: &str = "ws://127.0.0.1:8888/ws";

/// How long one idle read of the socket blocks. This is the loop's heartbeat, and the ceiling on how late a frame
/// can be noticed: the client wakes twice a millisecond rather than spinning.
const READ_TIMEOUT: Duration = Duration::from_micros(500);

pub type Socket = WebSocket<MaybeTlsStream<TcpStream>>;

pub enum Incoming {
  Frame(String),
  Idle,
  /// The peer sent a close frame, and `code` is why. It is not the same as [`Incoming::Closed`], which is the socket
  /// failing: one is a door shutting, the other is the building leaving.
  Detached { code: u16 },
  Closed,
}

/// RFC 6455's "normal closure", which is what the server sends when a window asks to leave (`:q`).
pub const NORMAL_CLOSE: u16 = 1000;

/// Whether this close is the server letting this window go, as opposed to closing for a reason nobody asked for.
///
/// It decides an exit code, and the launcher reads that as "keep the server running", so a close that merely happens to
/// carry no code — a peer hanging up — must not answer true.
pub fn is_farewell(code: u16) -> bool {
  code == NORMAL_CLOSE
}

pub fn connect() -> Result<Socket, tungstenite::Error> {
  // VISUR_WS points the client at another endpoint, which is what lets a test stand a fake server in for the real one.
  let url = std::env::var("VISUR_WS").unwrap_or_else(|_| URL.to_string());
  let (mut socket, _) = connect_ws(url)?;
  if let MaybeTlsStream::Plain(stream) = socket.get_mut() {
    let _ = stream.set_nodelay(true); // a keystroke is one small frame; do not wait to fill a segment
    let _ = stream.set_read_timeout(Some(READ_TIMEOUT));
  }
  Ok(socket)
}

fn send(socket: &mut Socket, address: &str, body: Value) {
  let frame = json!({ "address": address, "body": body }).to_string();
  let trace = std::env::var_os("VISUR_TRACE").is_some();
  match socket.send(Message::Text(frame.into())) {
    Ok(()) => {
      if trace {
        eprintln!("\r\ntrace: sent {address} {body}");
      }
    }
    Err(error) => {
      // Nothing useful to do here: the loop sees the closed socket on its next read and restores the terminal.
      if trace {
        eprintln!("\r\ntrace: send failed {error}");
      }
    }
  }
}

/// The viewport declaration. Once at attach is enough: the server binds it to this socket and remembers it until
/// the socket closes, and another client declaring a different window no longer moves this one's lines.
pub fn declare(socket: &mut Socket, cols: usize, rows: usize) {
  // `client` is one word of self-description: it decides which directory an unsaved nameless buffer's swap is claimed
  // from, and for the terminal that is the directory it was launched in, the same reason a swap belongs beside a file.
  send(socket, "viewportWasChanged", json!({ "cols": cols, "rows": rows, "client": "terminal" }));
}

pub fn send_key(socket: &mut Socket, token: &str) {
  send(socket, "keyWasPressed", json!({ "key": token }));
}

pub fn send_paste(socket: &mut Socket, text: &str) {
  send(socket, "pasteWasReceived", json!({ "text": text }));
}

#[cfg(test)]
mod tests {
  use super::*;

  /// The whole contract of an exit code, and worth a test of its own because the launcher's behaviour branches on it:
  /// a close that was not asked for must not look like one that was.
  #[test]
  fn only_a_normal_close_is_the_server_letting_this_window_go() {
    assert!(is_farewell(NORMAL_CLOSE), "`:q` closes with the protocol's normal-closure code");
    assert!(!is_farewell(1011), "a server closing because it broke is not a detach");
    assert!(!is_farewell(u16::MAX), "and a peer that hung up without a code is not one either");
  }
}

/// Whatever the socket has right now, one frame at a time.
pub fn next(socket: &mut Socket) -> Incoming {
  match socket.read() {
    Ok(Message::Text(text)) => Incoming::Frame(text.to_string()),
    Ok(Message::Close(frame)) => Incoming::Detached {
      // No code in the frame is not "code 1000"; it is a peer that hung up without saying why, and the client must not
      // tell the launcher to leave that server running.
      code: frame.map(|close| u16::from(close.code)).unwrap_or(u16::MAX),
    },
    Ok(_) => Incoming::Idle,
    Err(tungstenite::Error::Io(error))
      if matches!(
        error.kind(),
        std::io::ErrorKind::WouldBlock | std::io::ErrorKind::TimedOut
      ) =>
    {
      Incoming::Idle
    }
    Err(_) => Incoming::Closed,
  }
}
