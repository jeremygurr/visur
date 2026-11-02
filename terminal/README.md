# visur-term

Visur's terminal client: one process, one thread, one loop, and no editor state. It paints the viewport the server
sends and forwards keys; it never edits the document or derives anything the server has not said.

Designed by [spec §5](../docs/wayfinder/renderer-delivery/spec.md) and
[the component design](../docs/wayfinder/renderer-delivery/design/terminal-client-components.md); built ticket by
ticket in [terminal-build](../docs/wayfinder/terminal-build/map.md).

| Module | Component (# in the design) | Responsibility |
|---|---|---|
| [`src/keys.rs`](src/keys.rs) | 2 key adapter | crossterm `Event` → Visur key token, or nothing |
| [`src/caps.rs`](src/caps.rs) | 7 capabilities | ask the terminal what it can do (`?2026`, kitty flags); silence means no |
| [`src/zseq.rs`](src/zseq.rs) | 7 quit | the `Z` prefix: `ZZ` save-and-detach, `ZQ` detach, `ZD` quit |
| [`src/protocol.rs`](src/protocol.rs) | 10 codec | the view payload, decoded, tolerant of unknown fields |
| [`src/screen.rs`](src/screen.rs) | 4 screen model | shadow + terminal size → a grid of cells, mode line included |
| [`src/paint.rs`](src/paint.rs) | 5 compositor, 6 escape emitter | two grids → damage as row runs, then bytes; or a full repaint |
| [`src/transport.rs`](src/transport.rs) | 9 transport | the `/ws` socket and its bus envelopes |
| [`src/lifecycle.rs`](src/lifecycle.rs) | 7 restore | the ordered restore, on every exit path including a panic |
| [`src/main.rs`](src/main.rs) | 7 lifecycle + loop | raw mode, alternate screen, the capability probe, the loop |

## Running it

From the repo root, [`./visur`](../visur) starts a server if none is running and connects the client to it; that is
the command to use. To run the pieces by hand:

```bash
./gradlew run                     # the server, in another shell
cargo run --release               # connects to ws://127.0.0.1:8888/ws
```

`ZZ` / `ZQ` leave the server running; `ZD` asks for it to stop. Quit is the client's own binding because nothing in
Visur quits and raw mode turns `Ctrl-C` into an ordinary byte.

## Testing

```bash
cargo test                       # 45 unit tests, four pure seams, no server needed
cargo test --test pty            # 6 checks in a pty; all but one use a fake server, so no JVM either
VISUR_E2E=1 cargo test --test pty -- --nocapture  # the fifth, against the real server
```

The pty tests open a pty, set its window size, type keys and read the escape stream back — the same job
the spike did with a Python script, so the repo stays Java, Rust and bash. It asserts on the screen's *shape* and on
a marker it types itself, never on the seed document, because the server's document is shared with every other
client and is not reset between runs. For a clean measurement, start a fresh server first.

Environment overrides, all optional: `VISUR_WS` (endpoint, default `ws://127.0.0.1:8888/ws`), `VISUR_TRACE` (log events
and keys to stderr), `VISUR_DUMP` (write the escape stream to a file when a test's client exits), `VISUR_E2E` (run the
check that needs a live server).

## Painting

Frames are damage, not screens: `paint::frame` diffs the grid against the one the terminal is already showing and sends
the rows that changed, ending with the cursor — a keystroke costs tens of bytes instead of a screen. Only `full: true`
on the payload, the first frame of a session, and a geometry change discard the cache.

To re-measure both paths (the numbers and the verdict live in
[ticket 06](../docs/wayfinder/terminal-build/issues/06-client-damage.md)):

```bash
cargo test --release --bin visur-term measure_both -- --ignored --nocapture
```
