//! The measurement harness for the conformance pass ([ticket 09](docs/wayfinder/terminal-build/issues/09-conformance-pass.md)).
//!
//! Not shipped and not a test: it drives a *running* server and prints the numbers the ticket's table needs, in a form
//! that can be pasted. Rust because the repo is Java, Rust and bash, and because the client already talks
//! WebSocket — so the same dependency measures the wire and the canvas.

mod browser;
mod ws;

fn main() {
  let args = std::env::args().skip(1).collect::<Vec<_>>();
  let command = args.first().map(String::as_str).unwrap_or_default();
  let number = |name: &str, fallback: usize| {
    args
      .iter()
      .position(|arg| arg == name)
      .and_then(|at| args.get(at + 1))
      .and_then(|value| value.parse().ok())
      .unwrap_or(fallback)
  };

  match command {
    // What one keystroke costs on the wire, and how long the server takes to answer it.
    "ws" => ws::run(number("--cols", 112), number("--rows", 45), number("--keys", 20)),
    // What one keystroke costs the browser: key to painted frame, through the real canvas.
    "browser" => browser::run(number("--cols", 112), number("--rows", 45), number("--samples", 20)),
    // Whether one view reaches every attached renderer: `publish` and not `send`.
    "publish" => ws::publish(number("--cols", 112), number("--rows", 45)),
    _ => {
      eprintln!("usage: visur-harness ws|browser|publish [--cols N] [--rows N] [--keys N] [--samples N]");
      std::process::exit(2);
    }
  }
}
