# harness

Measurement tooling for the [conformance pass](../docs/wayfinder/terminal-build/conformance.md). Not shipped, not
imported by anything: it drives a **running** server and prints the numbers the ticket's table needs.

Rust because the repo is Java, Rust and bash, and because it can talk WebSocket and the DevTools protocol with the
dependencies the terminal client already has — so there is no Node, no Playwright and no second language.

| Command | Measures |
|---|---|
| `ws --cols N --rows N` | the view one keystroke produces: bytes, round trip, the 17-field contract, `full` only on a declaration, no newline inside a row |
| `publish` | that one view reaches **both** attached sockets (`publish`, not `send`) |
| `browser --cols N --rows N` | keystroke → painted cell in the canvas, split into dispatch / transport / paint, over CDP |
| [`sweep.sh`](sweep.sh) | the whole table: 3 document sizes × 2 viewports for both renderers, one fresh server each |

```bash
cargo build --release --manifest-path harness/Cargo.toml
./visur                                    # or start a server yourself
./harness/target/release/visur-harness ws --cols 100 --rows 30
./harness/target/release/visur-harness publish
VISUR_HEADED=1 ./harness/target/release/visur-harness browser --cols 112 --rows 45
./harness/sweep.sh                         # all of it, from a cold server each time
```

## Two things this harness knows that are easy to get wrong

- **Headless is not a measurement environment.** A hidden window's frames are throttled, so the canvas reports ~51 ms
  instead of ~15 ms. The browser command therefore opens a real window; `VISUR_HEADED` is only needed to be explicit.
- **The document is shared.** The server's editor model outlives a client, so a run that leaves text or insert mode
  behind makes the next run a different measurement. `sweep.sh` restarts the server per document size.

Chrome comes from `VISUR_CHROME`, or from the browser Playwright already downloaded
(`~/Library/Caches/ms-playwright`). Nothing else is required beyond a server on `127.0.0.1:8888`.
