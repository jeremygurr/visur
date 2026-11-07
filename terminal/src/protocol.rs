// Component 10 of the design: the view payload, decoded. Tolerant on purpose — the server may add fields, and
// the browser's SockJS bridge sends the same message with a string body.

use serde_json::Value;

#[derive(Debug, Clone, PartialEq, Eq, Default)]
pub struct Row {
  pub start: i64,
  pub text: String,
}

#[derive(Debug, Clone, PartialEq, Eq, Default)]
pub struct ViewUpdate {
  pub full: bool,
  pub origin: i64,
  pub rows: Vec<Row>,
  pub cursor_quantum_start: i64,
  pub cursor_quantum_end: i64,
  pub cursor_row: i64,
  pub cursor_col: i64,
  pub is_at_quantum_start: bool,
  pub span: i64,
  pub include_separators: bool,
  pub cursor_quantum: String,
  pub scope_quantum: String,
  pub editor_mode: String,
  pub editor_submode: String,
  pub is_in_command_state: bool,
  pub command_state_content: String,
  pub command_cursor: i64,
  pub search_target: String,
  /// The count typed in front of a key and not yet spent; zero when none was typed (spec §5.9).
  /// Whether this text holds work no file has. The document answers it, so two clients on one document draw the same
  /// marker.
  pub is_dirty: bool,
  pub pending_count: i64,
  /// What went wrong the last time the user asked for something; empty when nothing did.
  pub message: String,
}

fn string(body: &Value, key: &str) -> String {
  body.get(key).and_then(Value::as_str).unwrap_or_default().to_string()
}

fn number(body: &Value, key: &str) -> i64 {
  body.get(key).and_then(Value::as_i64).unwrap_or_default()
}

fn flag(body: &Value, key: &str) -> bool {
  body.get(key).and_then(Value::as_bool).unwrap_or_default()
}

fn rows(body: &Value) -> Vec<Row> {
  body
    .get("rows")
    .and_then(Value::as_array)
    .map(|rows| {
      rows
        .iter()
        .map(|row| Row { start: number(row, "start"), text: string(row, "text") })
        .collect()
    })
    .unwrap_or_default()
}

/// What a frame from the server is, as far as this client is concerned.
///
/// Three addresses mean something here, and the list is short on purpose: the server publishes things at itself that a
/// client has no business acting on, and a client that acted on frames it never asked for has a feature nobody designed.
/// Dropping an unknown *reply* is harmless; dropping an unknown *request* is how a feature silently does nothing, which
/// is why `clipboardWriteRequested` is routed here at all rather than pattern-matched in the loop.
#[derive(Debug, Clone, PartialEq, Eq)]
pub enum Inbound {
  /// What to paint: one complete viewport, every time.
  View(ViewUpdate),
  /// Put this text into the clipboard of the machine this client is running on.
  WriteClipboard(String),
  /// Say what that clipboard holds: the half a terminal mostly cannot do, and the half this client answers honestly by
  /// saying nothing rather than something stale.
  ReadClipboard,
  /// Anything else, including every address a newer server invents.
  Unknown,
}

/// One frame in, one thing to do out.
pub fn inbound(frame: &str) -> Inbound {
  let envelope = match serde_json::from_str::<Value>(frame) {
    Ok(envelope) => envelope,
    Err(_) => return Inbound::Unknown,
  };
  match envelope.get("address").and_then(Value::as_str).unwrap_or_default() {
    "viewWasChanged" => body_of(&envelope).and_then(|body| view(body).map(Inbound::View)).unwrap_or(Inbound::Unknown),
    "clipboardWriteRequested" => body_of(&envelope)
      .and_then(|body| body.get("text").and_then(Value::as_str).map(str::to_string))
      .map_or(Inbound::Unknown, Inbound::WriteClipboard),
    "clipboardReadRequested" => Inbound::ReadClipboard,
    _ => Inbound::Unknown,
  }
}

/// The body of a frame, decoded: plain, or double-encoded as a JSON string, which is what a server sends when it puts
/// a payload on the bus as text.
fn body_of(envelope: &Value) -> Option<Value> {
  match envelope.get("body")? {
    Value::String(text) => serde_json::from_str(text).ok(),
    other => Some(other.clone()),
  }
}

fn view(body: Value) -> Option<ViewUpdate> {
  Some(ViewUpdate {
    full: flag(&body, "full"),
    origin: number(&body, "origin"),
    rows: rows(&body),
    cursor_quantum_start: number(&body, "cursorQuantumStart"),
    cursor_quantum_end: number(&body, "cursorQuantumEnd"),
    cursor_row: number(&body, "cursorRow"),
    cursor_col: number(&body, "cursorCol"),
    is_at_quantum_start: flag(&body, "isAtQuantumStart"),
    span: number(&body, "span"),
    include_separators: flag(&body, "includeSeparators"),
    cursor_quantum: string(&body, "cursorQuantum"),
    scope_quantum: string(&body, "scopeQuantum"),
    editor_mode: string(&body, "editorMode"),
    editor_submode: string(&body, "editorSubmode"),
    is_in_command_state: flag(&body, "isInCommandState"),
    command_state_content: string(&body, "commandStateContent"),
    command_cursor: number(&body, "commandCursor"),
    search_target: string(&body, "searchTarget"),
    is_dirty: flag(&body, "isDirty"),
    pending_count: number(&body, "pendingCount"),
    message: string(&body, "message"),
  })
}

#[cfg(test)]
mod tests {
  use super::*;

  // A hand-written instance of the payload spec §1.2 defines; expectations below are read off this literal,
  // not recomputed from the parser.
  const FRAME: &str = r#"{"address":"viewWasChanged","body":{"full":true,"origin":112,
    "rows":[{"start":112,"text":"the quick "},{"start":122,"text":"brown fox"}],
    "span":1,"cursorQuantumStart":13,"cursorQuantumEnd":14,"cursorRow":2,"cursorCol":7,
    "isAtQuantumStart":true,"cursorQuantum":"character","scopeQuantum":"line",
    "editorMode":"navigate","editorSubmode":"character","isInCommandState":false,
    "commandStateContent":"","commandCursor":0,"searchTarget":"necess","pendingCount":4,
    "message":"loadFile: file not found: /tmp/gone.txt","isDirty":true}}"#;

  /// The one helper the field assertions need: a view, or a failure that says what the frame was.
  fn view_of(frame: &str) -> ViewUpdate {
    match inbound(frame) {
      Inbound::View(view) => view,
      other => panic!("expected a view, got {other:?}"),
    }
  }

  #[test]
  fn decodes_every_field_of_the_view_payload() {
    let view = view_of(FRAME);
    assert!(view.full);
    assert_eq!(view.origin, 112);
    assert_eq!(
      view.rows,
      vec![
        Row { start: 112, text: "the quick ".to_string() },
        Row { start: 122, text: "brown fox".to_string() },
      ]
    );
    assert_eq!((view.cursor_quantum_start, view.cursor_quantum_end), (13, 14));
    assert_eq!((view.cursor_row, view.cursor_col), (2, 7));
    assert!(view.is_at_quantum_start);
    assert_eq!(view.span, 1);
    assert_eq!(view.cursor_quantum, "character");
    assert_eq!(view.scope_quantum, "line");
    assert_eq!(view.editor_mode, "navigate");
    assert_eq!(view.editor_submode, "character");
    assert!(!view.is_in_command_state);
    assert_eq!(view.command_state_content, "");
    assert_eq!(view.command_cursor, 0);
    assert_eq!(view.search_target, "necess");
    assert!(view.is_dirty);
    assert_eq!(view.pending_count, 4);
    assert_eq!(view.message, "loadFile: file not found: /tmp/gone.txt");
  }

  /// The whole vocabulary this client speaks, and it is three addresses: a view to paint, a clipboard to write, and a
  /// clipboard to read. Everything else is dropped — `keyWasPressed` and `modelWasChanged` are things a client *sends*,
  /// and `connectionShouldClose` is a close frame rather than a message — including an address a newer server has not
  /// invented yet, which has to be nothing at all rather than a panic in a client that is already on screen.
  #[test]
  fn three_addresses_mean_something_and_the_rest_of_the_bus_does_not() {
    assert!(matches!(inbound(FRAME), Inbound::View(_)));
    assert_eq!(
      inbound(r#"{"address":"clipboardWriteRequested","body":{"text":"one "}}"#),
      Inbound::WriteClipboard("one ".to_string())
    );
    assert_eq!(inbound(r#"{"address":"clipboardReadRequested","body":{}}"#), Inbound::ReadClipboard);

    assert_eq!(inbound(r#"{"address":"keyWasPressed","body":{"key":"j"}}"#), Inbound::Unknown);
    assert_eq!(inbound(r#"{"address":"modelWasChanged","body":{}}"#), Inbound::Unknown);
    assert_eq!(inbound(r#"{"address":"connectionShouldClose","body":null}"#), Inbound::Unknown);
    assert_eq!(inbound(r#"{"address":"whateverComesNext","body":{"a":1}}"#), Inbound::Unknown);
    assert_eq!(inbound("not json at all"), Inbound::Unknown, "a frame that is not JSON is not a request");
  }

  #[test]
  fn a_write_with_no_text_is_not_a_write_of_nothing() {
    // An empty clipboard is written with `"text": ""`, which arrives as a write of "". No text field at all is a
    // protocol mistake, and the two are not the same message: one says "put nothing here", the other says nothing.
    assert_eq!(inbound(r#"{"address":"clipboardWriteRequested","body":{"text":""}}"#), Inbound::WriteClipboard(String::new()));
    assert_eq!(inbound(r#"{"address":"clipboardWriteRequested","body":{}}"#), Inbound::Unknown);
  }

  #[test]
  fn tolerates_an_unknown_field_and_a_string_body() {
    let extra = FRAME.replace(r#""span":1"#, r#""span":1,"somethingNewer":"x""#);
    assert!(matches!(inbound(&extra), Inbound::View(_)), "an added field must not break an older client");

    // A body carried as a JSON string inside the frame is the same view, and the server does this today for the view
    // payload it puts on the bus as text.
    let doubled = r#"{"address":"viewWasChanged","body":"{\"rows\":[{\"start\":0,\"text\":\"hi\"}],\"cursorRow\":0}"}"#;
    assert_eq!(view_of(doubled).rows[0].text, "hi");
  }

  #[test]
  fn defaults_absent_optional_fields() {
    let sparse = r#"{"address":"viewWasChanged","body":{"rows":[],"cursorRow":0,"cursorCol":0}}"#;
    let view = view_of(sparse);
    assert_eq!(view.span, 0);
    assert_eq!(view.search_target, "");
    assert_eq!(view.pending_count, 0, "no count typed is no count shown");
    assert_eq!(view.message, "", "nothing reported draws nothing");
    assert!(!view.full);
  }
}
