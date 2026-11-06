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
  Closed,
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
  send(socket, "viewportWasChanged", json!({ "cols": cols, "rows": rows }));
}

pub fn send_key(socket: &mut Socket, token: &str) {
  send(socket, "keyWasPressed", json!({ "key": token }));
}

pub fn send_paste(socket: &mut Socket, text: &str) {
  send(socket, "pasteWasReceived", json!({ "text": text }));
}

/// Whatever the socket has right now, one frame at a time.
pub fn next(socket: &mut Socket) -> Incoming {
  match socket.read() {
    Ok(Message::Text(text)) => Incoming::Frame(text.to_string()),
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
