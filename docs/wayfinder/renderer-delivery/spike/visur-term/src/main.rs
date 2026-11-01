// Visur terminal spike — throwaway. Deliberately Kilo-shaped: full repaint per view
// update, no cell-grid diffing, no compositor. The point is to feel the round trip,
// not to be fast. See docs/wayfinder/renderer-delivery/issues/07-kilo-spike.md
//
// Transport: minimal SockJS-over-WebSocket client, so no server change is needed.
// Frames: server -> "o", "h", "a[...]"; client -> ["<json string>"].

use crossterm::event::{self, Event, KeyCode, KeyEvent, KeyEventKind, KeyModifiers};
use crossterm::terminal;
use serde_json::{json, Value};
use std::io::Write;
use std::time::{Duration, Instant};
use tungstenite::stream::MaybeTlsStream;
use tungstenite::{connect, Message};

// ---------------------------------------------------------------- view payload

#[derive(Debug, Default, Clone)]
struct View {
    content: Vec<char>,
    cqs: i64,
    cqe: i64,
    span: i64,
    at_start: bool,
    mode: String,
    submode: String,
    quantum: String,
    scope: String,
    in_cmd: bool,
    cmd_content: String,
    cmd_cursor: i64,
    search_target: String,
}

fn s(v: &Value, k: &str) -> String {
    v.get(k).and_then(|x| x.as_str()).unwrap_or("").to_string()
}
fn i(v: &Value, k: &str) -> i64 {
    v.get(k).and_then(|x| x.as_i64()).unwrap_or(0)
}
fn b(v: &Value, k: &str) -> bool {
    v.get(k).and_then(|x| x.as_bool()).unwrap_or(false)
}

fn parse_view(body: &Value) -> View {
    View {
        content: s(body, "editorContent").chars().collect(),
        cqs: i(body, "cursorQuantumStart"),
        cqe: i(body, "cursorQuantumEnd"),
        span: i(body, "span"),
        at_start: b(body, "isAtQuantumStart"),
        mode: s(body, "editorMode"),
        submode: s(body, "editorSubmode"),
        quantum: s(body, "cursorQuantum"),
        scope: s(body, "scopeQuantum"),
        in_cmd: b(body, "isInCommandState"),
        cmd_content: s(body, "commandStateContent"),
        cmd_cursor: i(body, "commandCursor"),
        search_target: s(body, "searchTarget"),
    }
}

// ---------------------------------------------------------------------- layout

/// Wrap the buffer at `width` the way the browser does (`x % canvasWidth`), keeping
/// each character's absolute index and its (row, col) so the cursor can be placed.
struct Layout {
    rows: Vec<Vec<(usize, char)>>,
    pos: Vec<(usize, usize)>,
}

fn layout(content: &[char], width: usize) -> Layout {
    let width = width.max(1);
    let mut rows: Vec<Vec<(usize, char)>> = vec![Vec::new()];
    let mut pos = Vec::with_capacity(content.len());
    let mut x = 0usize;
    for (idx, ch) in content.iter().enumerate() {
        pos.push((rows.len() - 1, x));
        if *ch == '\n' {
            rows.push(Vec::new());
            x = 0;
            continue;
        }
        rows.last_mut().unwrap().push((idx, *ch));
        x += 1;
        if x == width {
            rows.push(Vec::new());
            x = 0;
        }
    }
    Layout { rows, pos }
}

fn cursor_cell(v: &View) -> usize {
    let cell = if v.span > 0 || v.at_start { v.cqs } else { v.cqe };
    cell.max(0) as usize
}

// ---------------------------------------------------------------------- render

fn paint(lay: &Layout, v: &View, start_row: usize, rows: usize, cols: usize, status: &str) -> String {
    let content_rows = rows.saturating_sub(1).max(1);
    let cursor = cursor_cell(v);
    let (cur_row, cur_col) = *lay.pos.get(cursor).unwrap_or(&(0, 0));
    let mut out = String::with_capacity(rows * (cols + 12) + 128);

    for r in 0..content_rows {
        out.push_str(&format!("\x1b[{};1H", r + 1));
        if let Some(row) = lay.rows.get(start_row + r) {
            for (idx, ch) in row {
                let on_cursor = *idx >= cursor
                    && (*idx == cursor || (v.span > 0 && (*idx as i64) < v.cqe.max(v.cqs)));
                if on_cursor {
                    out.push_str("\x1b[7m");
                }
                out.push(*ch);
                if on_cursor {
                    out.push_str("\x1b[0m");
                }
            }
        }
        out.push_str("\x1b[K");
    }

    // mode line
    let label = if v.in_cmd {
        let mut line = String::from(";");
        let chars: Vec<char> = v.cmd_content.chars().collect();
        for (x, ch) in chars.iter().enumerate() {
            line.push(if x as i64 == v.cmd_cursor { '|' } else { ' ' });
            line.push(*ch);
        }
        if v.cmd_cursor as usize >= chars.len() {
            line.push('|');
        }
        line
    } else if v.mode == v.submode {
        format!(" {} MODE ", v.mode.to_uppercase())
    } else {
        format!(" {} SUBMODE ", v.submode.to_uppercase())
    };
    let tail = if v.submode == "searchForward" || v.submode == "searchBackward" {
        format!(" {}({}) {} ", v.scope.to_uppercase(),
            if v.submode == "searchForward" { 'f' } else { 'F' }, v.search_target)
    } else {
        format!(" {} QUANTUM  SPAN={} ", v.quantum.to_uppercase(), v.span)
    };
    let mut line = format!("{}{}", label, tail);
    if !status.is_empty() {
        line.push_str(status);
    }
    for _ in line.chars().count()..cols {
        line.push(' ');
    }
    out.push_str(&format!("\x1b[{};1H\x1b[7m{}\x1b[0m", rows, line));

    // cursor
    let screen_row = cur_row.saturating_sub(start_row).min(content_rows - 1) + 1;
    let screen_col = cur_col.min(cols.saturating_sub(1)) + 1;
    out.push_str(&format!("\x1b[{};{}H", screen_row, screen_col));
    out
}

// ------------------------------------------------------------------------ keys

fn key_token(k: KeyEvent) -> Option<String> {
    let tok = match k.code {
        KeyCode::Esc => "Escape".to_string(),
        KeyCode::Enter => "Enter".to_string(),
        KeyCode::Backspace => "Backspace".to_string(),
        KeyCode::Left => "ArrowLeft".to_string(),
        KeyCode::Right => "ArrowRight".to_string(),
        KeyCode::Up => "ArrowUp".to_string(),
        KeyCode::Down => "ArrowDown".to_string(),
        KeyCode::Char(c) => c.to_string(),
        _ => return None,
    };
    // Research finding: drop any non-empty modifier set, or Ctrl-d arrives as
    // Char('d') and fires the navigate binding d = deleteCursorQuantum.
    if !k.modifiers.is_empty() {
        return None;
    }
    Some(tok)
}

// ------------------------------------------------------------------- transport

fn sockjs_url() -> String {
    let n = std::time::SystemTime::now()
        .duration_since(std::time::UNIX_EPOCH)
        .unwrap()
        .as_nanos() as u64;
    let alphabet = b"abcdefghijklmnopqrstuvwxyz0123456789";
    let session: String = (0..8)
        .map(|i| alphabet[((n >> (i * 3)) % 36) as usize] as char)
        .collect();
    format!("ws://127.0.0.1:8888/eventbus/{:03}/{}/websocket", n % 1000, session)
}

fn frame(v: Value) -> Message {
    let arr = serde_json::to_string(&vec![v.to_string()]).unwrap();
    Message::Text(arr.into())
}

fn send_event(ws: &mut tungstenite::WebSocket<MaybeTlsStream<std::net::TcpStream>>, address: &str, body: Value) {
    let body = if body.is_string() {
        body.as_str().unwrap().to_string()
    } else {
        body.to_string()
    };
    ws.send(frame(json!({"type": "send", "address": address, "body": body}))).ok();
}

// ------------------------------------------------------------------------ main

fn main() {
    let mut ws = match connect(sockjs_url()) {
        Ok((ws, _)) => ws,
        Err(e) => {
            eprintln!("connect failed: {e}\n(is the server running? try ./gradlew run)");
            std::process::exit(1);
        }
    };
    ws.send(frame(json!({"type": "register", "address": "viewWasChanged"}))).ok();

    let (cols, rows) = terminal::size().unwrap_or((100, 30));
    let (cols, rows) = (cols as usize, rows as usize);
    let content_rows = rows.saturating_sub(1).max(1);

    send_event(&mut ws, "canvasWasChanged", json!({"width": cols, "height": content_rows}));

    // Blocking socket with a short read timeout: reads are resumable across
    // WouldBlock in tungstenite, but a WouldBlock mid-write is not — so writes
    // stay blocking and only reads are time-limited.
    if let MaybeTlsStream::Plain(s) = ws.get_mut() {
        let _ = s.set_nodelay(true);
        let _ = s.set_read_timeout(Some(Duration::from_micros(200)));
    }

    terminal::enable_raw_mode().expect("raw mode");
    let mut out = std::io::stdout();
    out.write_all(b"\x1b[?1049h\x1b[?25l").ok();
    out.flush().ok();

    let mut view = View::default();
    let mut start_row = 0usize;
    let mut latencies: Vec<f64> = Vec::new();
    let mut last_key: Option<Instant> = None;
    let mut seen = 0usize;
    let mut dirty = true;
    let mut fatal: Option<String> = None;
    let mut last_ping = Instant::now();
    let mut last_nudge = Instant::now();
    let mut nudges = 0u32;

    'outer: loop {
        // The vert.x eventbus JS client pings every ~5s; without it the SockJS
        // session is reaped after 10s with c[3000,"Go away!"].
        // Readiness is a handshake, not an open port: the verticles may not be
        // subscribed yet when we connect, so keep asking until a view arrives.
        if seen == 0 && last_nudge.elapsed() > Duration::from_millis(300) && nudges < 60 {
            send_event(&mut ws, "canvasWasChanged", json!({"width": cols, "height": content_rows}));
            last_nudge = Instant::now();
            nudges += 1;
        }
        if last_ping.elapsed() > Duration::from_secs(4) {
            ws.send(frame(json!({"type": "ping"}))).ok();
            last_ping = Instant::now();
        }

        // drain everything the socket has right now
        loop {
            match ws.read() {
                Ok(Message::Text(t)) => {
                    let text = t.as_str();
                    if std::env::var("VISUR_SPIKE_DEBUG").is_ok() {
                        eprintln!("<< {}", &text.chars().take(160).collect::<String>());
                    }
                    // SockJS heartbeat frames ("h") need no reply; the session is
                    // kept alive by our periodic {"type":"ping"} instead.
                    if text == "h" {
                        continue;
                    }
                    if let Some(rest) = text.strip_prefix('a') {
                        if let Ok(items) = serde_json::from_str::<Vec<String>>(rest) {
                            for item in items {
                                let Ok(v) = serde_json::from_str::<Value>(&item) else { continue };
                                if v.get("address").and_then(|a| a.as_str()) != Some("viewWasChanged") {
                                    continue;
                                }
                                if let Some(body) = v.get("body") {
                                    view = parse_view(body);
                                    seen += 1;
                                    if let Some(t0) = last_key.take() {
                                        latencies.push(t0.elapsed().as_secs_f64() * 1000.0);
                                    }
                                    dirty = true;
                                }
                            }
                        }
                    }
                }
                Ok(_) => {}
                Err(tungstenite::Error::Io(e))
                    if matches!(
                        e.kind(),
                        std::io::ErrorKind::WouldBlock | std::io::ErrorKind::TimedOut
                    ) => break,
                Err(tungstenite::Error::ConnectionClosed | tungstenite::Error::AlreadyClosed) => {
                    fatal = Some("server closed the connection".to_string());
                    break 'outer;
                }
                Err(e) => {
                    fatal = Some(format!("transport error: {e}"));
                    break 'outer;
                }
            }
        }

        if event::poll(Duration::ZERO).unwrap_or(false) {
            if let Ok(Event::Key(k)) = event::read() {
                if k.kind == KeyEventKind::Release {
                    continue;
                }
                let ctrl = k.modifiers.contains(KeyModifiers::CONTROL);
                if ctrl && matches!(k.code, KeyCode::Char('c') | KeyCode::Char('q')) {
                    break 'outer;
                }
                if let Some(tok) = key_token(k) {
                    last_key = Some(Instant::now());
                    send_event(&mut ws, "keyWasPressed", json!({"key": tok}));
                }
            }
        }

        if dirty {
            let lay = layout(&view.content, cols);
            let cursor = cursor_cell(&view);
            let (cur_row, _) = *lay.pos.get(cursor).unwrap_or(&(0, 0));
            if cur_row < start_row {
                start_row = cur_row;
            } else if cur_row >= start_row + content_rows {
                start_row = cur_row + 1 - content_rows;
            }
            let status = match latencies.last() {
                Some(ms) => format!("  last={:.1}ms n={} views={}", ms, latencies.len(), seen),
                None => format!("  waiting for view (views={})", seen),
            };
            let screen = paint(&lay, &view, start_row, rows, cols, &status);
            out.write_all(screen.as_bytes()).ok();
            out.flush().ok();
            dirty = false;
        }

    }

    out.write_all(b"\x1b[0m\x1b[?25h\x1b[?1049l").ok();
    out.flush().ok();
    terminal::disable_raw_mode().ok();

    if !latencies.is_empty() {
        let mut sorted = latencies.clone();
        sorted.sort_by(|a, b| a.partial_cmp(b).unwrap());
        let median = sorted[sorted.len() / 2];
        let p95 = sorted[((sorted.len() as f64 * 0.95) as usize).min(sorted.len() - 1)];
        eprintln!(
            "keystroke -> view rendered: median {:.1} ms, p95 {:.1} ms, n={}",
            median, p95, sorted.len()
        );
    }
    if let Some(e) = fatal {
        eprintln!("{e}");
    }
}
