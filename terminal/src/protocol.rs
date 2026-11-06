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

/// One frame in, one view out — or nothing if the frame is not a view.
pub fn parse(frame: &str) -> Option<ViewUpdate> {
  let envelope: Value = serde_json::from_str(frame).ok()?;
  if envelope.get("address").and_then(Value::as_str) != Some("viewWasChanged") {
    return None;
  }
  let raw = envelope.get("body")?;
  let body: Value = match raw {
    Value::String(text) => serde_json::from_str(text).ok()?,
    other => other.clone(),
  };
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
    "message":"loadFile: file not found: /tmp/gone.txt"}}"#;

  #[test]
  fn decodes_every_field_of_the_view_payload() {
    let view = parse(FRAME).expect("a view frame");
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
    assert_eq!(view.pending_count, 4);
    assert_eq!(view.message, "loadFile: file not found: /tmp/gone.txt");
  }

  #[test]
  fn ignores_frames_that_are_not_views() {
    assert_eq!(parse(r#"{"address":"keyWasPressed","body":{"key":"j"}}"#), None);
    assert_eq!(parse(r#"{"address":"modelWasChanged","body":{}}"#), None);
  }

  #[test]
  fn tolerates_an_unknown_field_and_a_string_body() {
    let extra = FRAME.replace(r#""span":1"#, r#""span":1,"somethingNewer":"x""#);
    assert!(parse(&extra).is_some(), "an added field must not break an older client");

    // The SockJS bridge sends the same message with the body as a JSON string.
    let doubled = r#"{"address":"viewWasChanged","body":"{\"rows\":[{\"start\":0,\"text\":\"hi\"}],\"cursorRow\":0}"}"#;
    assert_eq!(parse(doubled).expect("a view frame").rows[0].text, "hi");
  }

  #[test]
  fn defaults_absent_optional_fields() {
    let sparse = r#"{"address":"viewWasChanged","body":{"rows":[],"cursorRow":0,"cursorCol":0}}"#;
    let view = parse(sparse).expect("a view frame");
    assert_eq!(view.span, 0);
    assert_eq!(view.search_target, "");
    assert_eq!(view.pending_count, 0, "no count typed is no count shown");
    assert_eq!(view.message, "", "nothing reported draws nothing");
    assert!(!view.full);
  }
}
