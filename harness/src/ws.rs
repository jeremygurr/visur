//! What one keystroke costs on the wire: the view the server publishes in answer, its size, and how long the answer
//! took. This is the payload half of the conformance table, and it is identical for both renderers — they read the
//! same message.

use std::net::TcpStream;
use std::time::{Duration, Instant};

use tungstenite::stream::MaybeTlsStream;
use tungstenite::{connect, Message, WebSocket};

const URL: &str = "ws://127.0.0.1:8888/ws";
/// The payload contract (spec §1.2). A frame that does not have exactly this many fields is a wire change, and the
/// harness says so rather than quietly averaging a different message.
const FIELDS: usize = 21;
const BUDGET_BYTES: usize = 8 * 1024;

type Socket = WebSocket<MaybeTlsStream<TcpStream>>;

/// Both renderers read the same message, so this also checks the parts of the contract neither renderer can see for
/// itself: `full` only on the answer to a declaration, and no newline inside a row.
pub fn run(cols: usize, rows: usize, keys: usize) {
  let (mut socket, _) = connect(URL).unwrap_or_else(|error| {
    eprintln!("cannot reach {URL}: {error} — start a server first (./visur)");
    std::process::exit(2);
  });
  if let MaybeTlsStream::Plain(stream) = socket.get_mut() {
    stream.set_read_timeout(Some(Duration::from_secs(10))).ok();
  }

  send(&mut socket, "viewportWasChanged", &format!(r#"{{"cols":{cols},"rows":{rows}}}"#));
  let declared = view(&mut socket).expect("the server did not answer the viewport declaration");
  let (view_rows, widest, fields) = shape(&declared).expect("the answer is not a view");
  assert_contract(&declared, fields);
  let declared_full = flag(&declared, "full");
  let embedded_newline = has_newline(&declared);

  let mut samples = Vec::new();
  for _ in 0..keys {
    let started = Instant::now();
    send(&mut socket, "keyWasPressed", r#"{"key":"j"}"#);
    match view(&mut socket) {
      // The *first* view answering a key is the one that matters; a later repaint is not this keystroke's latency.
      Some(frame) => {
        if flag(&frame, "full") {
          eprintln!("a keystroke answered with full: true — only a declaration should: {frame}");
          std::process::exit(1);
        }
        samples.push((started.elapsed(), frame.len()));
      }
      None => break,
    }
  }

  let bytes = samples.iter().map(|(_, bytes)| *bytes).collect::<Vec<_>>();
  let times = samples.iter().map(|(took, _)| *took).collect::<Vec<_>>();
  // "Flat" is the spec's claim, not byte-identical frames: cursor digits change width as the cursor moves, so allow
  // a couple of bytes of drift and fail on anything proportional to the document.
  let (low, high) = (bytes.iter().min().copied().unwrap_or(0), bytes.iter().max().copied().unwrap_or(0));
  let flat = high - low <= high / 50;
  println!(
    "ws        rows_in_view={view_rows} widest_row={widest} bytes_median={} bytes_min={} bytes_max={} \
     flat={} roundtrip_ms={:.2} fields={fields}",
    median_of(bytes.clone()).unwrap_or(0),
    low,
    high,
    flat,
    median(times).unwrap_or_default(),
  );

  println!(
    "ws        declaration_full={declared_full} newline_inside_a_row={embedded_newline} keystrokes_full=never"
  );

  let size = median_of(bytes).unwrap_or_default();
  let ok = flat && size <= BUDGET_BYTES && fields == FIELDS && declared_full && !embedded_newline;
  println!("ws        verdict={}", if ok { "met" } else { "not met" });
  if !ok {
    std::process::exit(1);
  }
}

fn send(socket: &mut Socket, address: &str, body: &str) {
  let frame = format!(r#"{{"address":"{address}","body":{body}}}"#);
  socket.send(Message::text(frame)).expect("the socket refused a frame");
}

/// A view is state for every attached renderer, so an answer to one client's key must reach all of them.
pub fn publish(cols: usize, rows: usize) {
  let (mut first, mut second) = (attach(cols, rows), attach(cols, rows));
  // Both declarations have to be answered before the keystroke, or "both saw it" would be satisfied by the greeting.
  let greeting = (view(&mut first).is_some(), view(&mut second).is_some());

  send(&mut first, "keyWasPressed", r#"{"key":"j"}"#);
  let seen = (view(&mut first).is_some(), view(&mut second).is_some());
  println!("publish   greeted=({},{}) after_one_keystroke=({},{})", greeting.0, greeting.1, seen.0, seen.1);
  let ok = greeting.0 && greeting.1 && seen.0 && seen.1;
  println!("publish   verdict={}", if ok { "met" } else { "not met" });
  if !ok {
    std::process::exit(1);
  }
}

fn attach(cols: usize, rows: usize) -> Socket {
  let (mut socket, _) = connect(URL).unwrap_or_else(|error| {
    eprintln!("cannot reach {URL}: {error}");
    std::process::exit(2);
  });
  if let MaybeTlsStream::Plain(stream) = socket.get_mut() {
    stream.set_read_timeout(Some(Duration::from_secs(10))).ok();
  }
  send(&mut socket, "viewportWasChanged", &format!(r#"{{"cols":{cols},"rows":{rows}}}"#));
  socket
}

fn flag(frame: &str, name: &str) -> bool {
  serde_json::from_str::<serde_json::Value>(frame)
    .ok()
    .map(|envelope| envelope["body"][name].as_bool().unwrap_or(false))
    .unwrap_or(false)
}

fn has_newline(frame: &str) -> bool {
  serde_json::from_str::<serde_json::Value>(frame)
    .ok()
    .and_then(|envelope| envelope["body"]["rows"].as_array().map(|rows| {
      rows.iter().any(|row| row["text"].as_str().is_some_and(|text| text.contains('\n')))
    }))
    .unwrap_or(true)
}

/// The next view the bus publishes to this socket, skipping anything that is not one.
fn view(socket: &mut Socket) -> Option<String> {
  loop {
    match socket.read() {
      Ok(Message::Text(text)) => {
        if text.contains(r#""viewWasChanged""#) {
          return Some(text.to_string());
        }
      }
      Ok(_) => {}
      Err(_) => return None,
    }
  }
}

/// How many rows arrived, and the width of the widest one: the view does not carry its own geometry.
fn shape(frame: &str) -> Option<(usize, usize, usize)> {
  let envelope: serde_json::Value = serde_json::from_str(frame).ok()?;
  let body = &envelope["body"];
  let rows = body["rows"].as_array()?;
  let widest = rows
    .iter()
    .map(|row| row["text"].as_str().unwrap_or_default().chars().count())
    .max()
    .unwrap_or(0);
  Some((rows.len(), widest, body.as_object()?.len()))
}

fn assert_contract(frame: &str, fields: usize) {
  if fields != FIELDS {
    eprintln!("the view carries {fields} fields, the contract has {FIELDS}: {frame}");
    std::process::exit(1);
  }
}

fn median_of(mut values: Vec<usize>) -> Option<usize> {
  values.sort_unstable();
  values.get(values.len() / 2).copied()
}

fn median(mut values: Vec<Duration>) -> Option<f64> {
  values.sort();
  Some(values.get(values.len() / 2)?.as_secs_f64() * 1000.0)
}
