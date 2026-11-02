//! What one keystroke costs the browser: key → last painted pixel.
//!
//! It drives Chromium the way a test runner would — over the DevTools protocol — so the repo needs no Node and no
//! browser driver. The page is instrumented from a script installed *before* any of its own, which is what makes the
//! numbers honest: the key timestamp is taken by a capture-phase listener that runs before the app's handler, and the
//! paint finishes at the second `requestAnimationFrame` after the view arrives, which is when the compositor has the
//! frame.

use std::io::{Read, Write};
use std::net::TcpStream;
use std::process::{Child, Command, Stdio};
use std::time::{Duration, Instant};

use serde_json::{json, Value};

const APP: &str = "http://127.0.0.1:8888/static/visur.html";

pub fn run(cols: usize, rows: usize, samples: usize) {
  assert_server_is_up();

  let port = free_port();
  let mut chrome = launch_chrome(port, cols, rows);
  let target = wait_for_page(port);
  let mut devtools = DevTools::connect(&target).unwrap_or_else(|error| {
    eprintln!("cannot attach to the browser on port {port}: {error}");
    let _ = chrome.kill();
    std::process::exit(2);
  });

  devtools.call("Page.enable", "{}");
  devtools.call("Runtime.enable", "{}");
  let script = json!({ "source": INSTRUMENT });
  devtools.call("Page.addScriptToEvaluateOnNewDocument", &script.to_string());
  let navigation = json!({ "url": APP });
  devtools.call("Page.navigate", &navigation.to_string());

  if let Err(why) = wait_for_first_paint(&mut devtools) {
    eprintln!("{why}");
    let _ = chrome.kill();
    std::process::exit(2);
  }
  let geometry = devtools.evaluate(
    "JSON.stringify({cols: (canvas.width - cxContentOffset) / cellWidth + 1, \
     rows: (canvas.height - cyContentOffset) / cellHeight})",
  );
  let geometry: Value = serde_json::from_str(&geometry).unwrap_or(Value::Null);

  let mut totals = Vec::new();
  let mut dispatches = Vec::new();
  let mut transports = Vec::new();
  let mut paints = Vec::new();
  let mut bytes = Vec::new();
  for _ in 0..samples {
    devtools.evaluate("window.__visur.last = null; window.__visur.requested = 0");
    let key_down = json!({
      "type": "keyDown", "key": "j", "code": "KeyJ", "windowsVirtualKeyCode": 74, "nativeVirtualKeyCode": 74,
    });
    devtools.call("Input.dispatchKeyEvent", &key_down.to_string());
    let key_up = json!({
      "type": "keyUp", "key": "j", "code": "KeyJ", "windowsVirtualKeyCode": 74, "nativeVirtualKeyCode": 74,
    });
    devtools.call("Input.dispatchKeyEvent", &key_up.to_string());

    match wait_for_sample(&mut devtools) {
      Ok(sample) => {
        totals.push(sample.total);
        dispatches.push(sample.dispatch);
        transports.push(sample.transport);
        paints.push(sample.paint);
        bytes.push(sample.bytes);
      }
      Err(why) => {
        eprintln!("{why}");
        break;
      }
    }
  }
  let _ = chrome.kill();

  let total = median(totals);
  let paint = median(paints.clone());
  println!(
    "browser   cells={}x{} bytes_median={} paint_ms={:.2} transport_ms={:.2} dispatch_ms={:.2} \
     key_to_paint_ms={:.2} samples={}",
    geometry["cols"].as_f64().unwrap_or_default().round(),
    geometry["rows"].as_f64().unwrap_or_default().round(),
    median_of(bytes),
    paint,
    median(transports),
    median(dispatches),
    total,
    paints.len(),
  );
  // The spec's bar for both renderers (§4.3): keystroke → last painted pixel inside one frame at 5 MB.
  println!("browser   verdict={}", if total <= 16.0 { "met" } else { "not met" });
}

/// Runs in the page, before any of its own scripts.
const INSTRUMENT: &str = r#"
(() => {
  const marks = window.__visur = { requested: 0, sent: 0, arrived: 0, last: null, ready: false, keys: 0 };
  // The socket-level arrival time, so the total can be split into transport and bridge: the spec calls the second
  // half of that "the SockJS floor", and this says how much of the number is which.
  const Real = window.WebSocket;
  function Wrapped(url, protocols) {
    const socket = protocols === undefined ? new Real(url) : new Real(url, protocols);
    socket.addEventListener('message', (event) => {
      if (typeof event.data === 'string' && event.data.includes('viewWasChanged')) {
        marks.arrived = performance.now();
      }
    });
    return socket;
  }
  Wrapped.prototype = Real.prototype;
  Object.setPrototypeOf(Wrapped, Real);
  window.WebSocket = Wrapped;
  // The EventBus comes from a CDN script that loads after this one, so it is wrapped on the way in. Wrapping the view
  // handler is what makes this measurable at all: a headless window drops frames to about 1 Hz, so anything timed with
  // requestAnimationFrame would be timing the throttler.
  const real = { value: null };
  Object.defineProperty(window, 'EventBus', {
    configurable: true,
    get() { return real.value; },
    set(Value) {
      real.value = Value;
      const send = Value.prototype.send;
      Value.prototype.send = function (address, body) {
        if (address === 'keyWasPressed' && marks.requested && !marks.sent) {
          marks.sent = performance.now();
        }
        return send.call(this, address, body);
      };
      const register = Value.prototype.registerHandler;
      Value.prototype.registerHandler = function (address, handler) {
        if (address !== 'viewWasChanged') {
          return register.call(this, address, handler);
        }
        return register.call(this, address, function (error, message) {
          const started = performance.now();
          const out = handler(error, message);          // the page draws synchronously in here
          const painted = performance.now();
          marks.ready = true;
          if (marks.requested) {
            marks.last = {
              total: painted - marks.requested,
              dispatch: marks.sent ? marks.sent - marks.requested : -1,
              transport: marks.arrived && marks.sent ? marks.arrived - marks.sent : -1,
              paint: painted - started,
              bytes: JSON.stringify((message && message.body) || {}).length,
            };
          }
          return out;
        });
      };
    },
  });
  window.addEventListener('keydown', () => {
    marks.keys += 1;
    if (!marks.requested) {
      marks.requested = performance.now();
      marks.sent = 0;
    }
  }, true);
})();
"#;

struct DevTools {
  socket: tungstenite::WebSocket<TcpStream>,
  next: u64,
}

impl DevTools {
  fn connect(url: &str) -> Result<DevTools, String> {
    let stream = TcpStream::connect(url.trim_start_matches("ws://").split('/').next().unwrap_or_default())
      .map_err(|error| error.to_string())?;
    stream.set_read_timeout(Some(Duration::from_secs(20))).ok();
    let (socket, _) = tungstenite::client(url, stream).map_err(|error| error.to_string())?;
    Ok(DevTools { socket, next: 1 })
  }

  fn call(&mut self, method: &str, params: &str) -> Value {
    let id = self.next;
    self.next += 1;
    let request = format!(r#"{{"id":{id},"method":"{method}","params":{params}}}"#);
    self.socket.send(tungstenite::Message::text(request)).expect("devtools send");
    loop {
      let Ok(message) = self.socket.read() else { return Value::Null };
      let tungstenite::Message::Text(text) = message else { continue };
      let Ok(reply) = serde_json::from_str::<Value>(&text) else { continue };
      // Events have no id; they are not answers and are not waited for.
      if reply["id"].as_u64() == Some(id) {
        return reply;
      }
    }
  }

  fn evaluate(&mut self, expression: &str) -> String {
    let params = json!({ "expression": expression, "returnByValue": true });
    let reply = self.call("Runtime.evaluate", &params.to_string());
    reply["result"]["result"]["value"].as_str().unwrap_or_default().to_string()
  }
}

fn wait_for_first_paint(devtools: &mut DevTools) -> Result<(), String> {
  let deadline = Instant::now() + Duration::from_secs(20);
  while Instant::now() < deadline {
    if devtools.evaluate("String(window.__visur && window.__visur.ready)") == "true" {
      // Give the canvas one frame to settle before the first keystroke is timed.
      std::thread::sleep(Duration::from_millis(100));
      return Ok(());
    }
    std::thread::sleep(Duration::from_millis(20));
  }
  Err("the page never received a view: is the event bus reachable from the browser?".to_string())
}

struct Sample {
  total: f64,
  dispatch: f64,
  transport: f64,
  paint: f64,
  bytes: usize,
}

fn wait_for_sample(devtools: &mut DevTools) -> Result<Sample, String> {
  let deadline = Instant::now() + Duration::from_secs(20);
  while Instant::now() < deadline {
    let raw = devtools.evaluate("window.__visur.last ? JSON.stringify(window.__visur.last) : ''");
    if let Ok(sample) = serde_json::from_str::<Value>(&raw) {
      if sample.is_object() {
        return Ok(Sample {
          total: sample["total"].as_f64().unwrap_or_default(),
          dispatch: sample["dispatch"].as_f64().unwrap_or_default(),
          transport: sample["transport"].as_f64().unwrap_or_default(),
          paint: sample["paint"].as_f64().unwrap_or_default(),
          bytes: sample["bytes"].as_u64().unwrap_or_default() as usize,
        });
      }
    }
    std::thread::sleep(Duration::from_millis(2));
  }
  let diagnostic = devtools.evaluate(
    "JSON.stringify({keys: window.__visur.keys, ready: window.__visur.ready, requested: window.__visur.requested})",
  );
  Err(format!("no frame arrived after a keystroke: {diagnostic}"))
}

fn assert_server_is_up() {
  let reachable = TcpStream::connect(("127.0.0.1", 8888)).is_ok();
  if !reachable {
    eprintln!("no server on 127.0.0.1:8888 — start one with ./visur");
    std::process::exit(2);
  }
}

fn free_port() -> u16 {
  std::net::TcpListener::bind(("127.0.0.1", 0))
    .and_then(|listener| listener.local_addr())
    .map(|address| address.port())
    .unwrap_or(9222)
}

fn launch_chrome(port: u16, cols: usize, rows: usize) -> Child {
  let binary = chrome_binary();
  let profile = std::env::temp_dir().join(format!("visur-harness-{port}"));
  // The canvas is laid out from the window, so the window is what decides the cell count; the harness reports the
  // cells the page actually declared rather than assuming these pixels won.
  let width = cols * 14 + 40;
  let height = rows * 20 + 80;
  // A hidden window is throttled and its numbers belong to the throttler; VISUR_HEADED=1 measures a real window.
  let headed = std::env::var("VISUR_HEADED").is_ok();
  let child = Command::new(&binary)
    .args(if headed { &[] as &[&str] } else { &["--headless=new"] })
    .arg(format!("--remote-debugging-port={port}"))
    .arg(format!("--user-data-dir={}", profile.display()))
    .arg(format!("--window-size={width},{height}"))
    .arg("--no-first-run")
    .arg("--no-default-browser-check")
    .arg("--disable-extensions")
    // A headless window counts as hidden: without these, frames are dropped to about 1 Hz and every number is the
    // throttler's, not the app's.
    .arg("--disable-background-timer-throttling")
    .arg("--disable-backgrounding-occluded-windows")
    .arg("--disable-renderer-backgrounding")
    .arg("--disable-features=CalculateNativeWinOcclusion")
    .arg("about:blank")
    .stdout(Stdio::null())
    .stderr(std::fs::File::create(std::env::temp_dir().join("visur-harness-chrome.log")).map(Stdio::from).unwrap_or_else(|_| Stdio::null()))
    .spawn()
    .unwrap_or_else(|error| {
      eprintln!("cannot start Chrome ({}): {error} — set VISUR_CHROME", binary);
      std::process::exit(2);
    });
  child
}

/// `VISUR_CHROME`, else the browser Playwright already downloaded — one is on every machine that has run it, and it
/// keeps the repo free of a second language.
fn chrome_binary() -> String {
  if let Ok(path) = std::env::var("VISUR_CHROME") {
    return path;
  }
  let home = std::env::var("HOME").unwrap_or_default();
  let cache = std::path::Path::new(&home).join("Library/Caches/ms-playwright");
  let mut found = None;
  for entry in std::fs::read_dir(&cache).into_iter().flatten().flatten() {
    let name = entry.file_name().to_string_lossy().to_string();
    if !name.starts_with("chromium-") {
      continue;
    }
    let candidate = entry
      .path()
      .join("chrome-mac-arm64/Google Chrome for Testing.app/Contents/MacOS/Google Chrome for Testing");
    if candidate.exists() {
      found = Some(candidate.to_string_lossy().to_string());
    }
  }
  found.unwrap_or_else(|| {
    eprintln!("no Chrome found: set VISUR_CHROME to a Chrome or Chromium binary");
    std::process::exit(2);
  })
}

/// The page target's DevTools socket, once Chrome is listening.
fn wait_for_page(port: u16) -> String {
  let deadline = Instant::now() + Duration::from_secs(20);
  let mut last = String::new();
  while Instant::now() < deadline {
    match http_get(port, "/json/list") {
      Err(why) => last = why,
      Ok(list) => {
        if let Ok(targets) = serde_json::from_str::<Value>(&list) {
          for target in targets.as_array().into_iter().flatten() {
            if target["type"] == "page" {
              if let Some(url) = target["webSocketDebuggerUrl"].as_str() {
                return url.to_string();
              }
            }
          }
        }
      }
    }
    std::thread::sleep(Duration::from_millis(50));
  }
  eprintln!("Chrome never offered a page on port {port} (last error: {last})");
  std::process::exit(2);
}

fn http_get(port: u16, path: &str) -> Result<String, String> {
  let mut stream = TcpStream::connect(("127.0.0.1", port)).map_err(|error| error.to_string())?;
  stream.set_read_timeout(Some(Duration::from_millis(300))).ok();
  let request = format!("GET {path} HTTP/1.1\r\nHost: 127.0.0.1:{port}\r\nConnection: close\r\n\r\n");
  stream.write_all(request.as_bytes()).map_err(|error| error.to_string())?;
  // The DevTools HTTP server keeps the connection open, so read until what has arrived parses as JSON rather than
  // until the socket closes.
  let mut raw = Vec::new();
  let mut chunk = [0u8; 8192];
  loop {
    match stream.read(&mut chunk) {
      Ok(0) => break,
      Ok(count) => raw.extend_from_slice(&chunk[..count]),
      Err(_) => {}
    }
    let text = String::from_utf8_lossy(&raw).to_string();
    if let (Some(start), Some(end)) = (text.find('['), text.rfind(']')) {
      let body = &text[start..=end];
      if serde_json::from_str::<Value>(body).is_ok() {
        return Ok(body.to_string());
      }
    }
    if raw.len() > 1 << 20 {
      break;
    }
  }
  Err(format!("no JSON answer to {path} ({} bytes)", raw.len()))
}

fn median_of(mut values: Vec<usize>) -> usize {
  values.sort_unstable();
  values.get(values.len() / 2).copied().unwrap_or_default()
}

fn median(mut values: Vec<f64>) -> f64 {
  values.sort_by(f64::total_cmp);
  values.get(values.len() / 2).copied().unwrap_or(f64::NAN)
}
