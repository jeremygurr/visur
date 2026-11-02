# visur-term

Visur's terminal client: one process, one thread, one loop, and no editor state. It paints the viewport the server
sends and forwards keys; it never edits the document or derives anything the server has not said.

Designed by [spec §5](../docs/wayfinder/renderer-delivery/spec.md) and
[the component design](../docs/wayfinder/renderer-delivery/design/terminal-client-components.md); built ticket by
ticket in [terminal-build](../docs/wayfinder/terminal-build/map.md).

| Module | Component (# in the design) | Responsibility |
|---|---|---|
| [`src/keys.rs`](src/keys.rs) | 2 key adapter | crossterm `Event` → Visur key token, or nothing |
| [`src/zseq.rs`](src/zseq.rs) | 7 quit | the `Z` prefix: `ZZ` save-and-detach, `ZQ` detach, `ZD` quit |
| [`src/protocol.rs`](src/protocol.rs) | 10 codec | the view payload, decoded, tolerant of unknown fields |
| [`src/screen.rs`](src/screen.rs) | 4 screen model | shadow + terminal size → a grid of cells, mode line included |
| [`src/paint.rs`](src/paint.rs) | 6 escape emitter | a grid → escape bytes, one string per frame |
| [`src/transport.rs`](src/transport.rs) | 9 transport | the `/ws` socket and its bus envelopes |
| [`src/main.rs`](src/main.rs) | 7 lifecycle + loop | raw mode, alternate screen, restore, the loop |

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
cargo test                                      # 28 unit tests, four pure seams, no server needed
VISUR_E2E=1 cargo test --test pty -- --nocapture  # drives the real client in a pty against a running server
```

The end-to-end test opens a pty, sets its window size, types keys and reads the escape stream back — the same job
the spike did with a Python script, so the repo stays Java, Rust and bash. It asserts on the screen's *shape* and on
a marker it types itself, never on the seed document, because the server's document is shared with every other
client and is not reset between runs. For a clean measurement, start a fresh server first.
