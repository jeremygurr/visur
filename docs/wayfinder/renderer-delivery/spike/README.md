# Visur terminal spike (throwaway)

Kilo-shaped: **full repaint every view update, no cell-grid diffing, no compositor.** It exists to answer one question — does the round trip feel usable, and where does it stop feeling usable? — not to be fast. See [the ticket](../issues/07-kilo-spike.md).

## Run it

```
./visur
```

One command. It uses a server already listening on `:8888`, otherwise starts one (`./gradlew run`), waits for it, and shuts down **only** a server it started. That is the launcher shape [ticket 04](../issues/04-decide-client-language.md) requires, built here so the spike is usable.

Build first if needed:

```
cd visur-term && cargo build --release
```

Requires the server's own `src/` to be unmodified — the spike speaks SockJS over WebSocket to the existing `/eventbus`, so **no Java change is needed**.

## What works

`h` `j` `k` `l` · `i` · `Esc` · printable characters · `Backspace` · arrows · `Ctrl-C` / `Ctrl-Q` to quit.

Mode line shows mode/submode, quantum, span, and the last measured keystroke→render latency. On exit it prints median and p95.

## Deliberately not here

Diffing, damage regions, scrollback beyond the viewport, mouse, resize handling (the size is read once at startup), `?2026`, truecolor, command state, search submodes, paste, and any Rust test coverage. `ratatui` is not used — that is the decision, not an omission.

## Three things it taught us, cheaper than the spec would have

1. **The SockJS session must be pinged.** The vert.x eventbus JS client sends `{"type":"ping"}` about every 5 s; without it the server reaps the session after exactly **10 s** with `c[3000,"Go away!"]`. A Rust client on this transport has to implement that, which is one concrete cost of reusing SockJS rather than a plain socket (ticket 08).
2. **Readiness is a handshake, not an open port.** The first `canvasWasChanged` is regularly lost because the verticles are not subscribed yet when the socket opens. The spike re-sends it every 300 ms until the first view arrives.
3. **The client must reimplement the browser's wrapping.** `x % canvasWidth` ([visur.js:167-171](../../../../src/main/resources/static/visur.js#L167-L171)) is needed just to know where to put the cursor, because the server sends an unwrapped character array. This is the wrapping hole in [the component design](../design/terminal-client-components.md), now observed rather than predicted.

## Files

- `visur-term/src/main.rs` — the whole client: SockJS framing, view parsing, layout, paint, key adapter
- `visur` — the launcher
- `drive.py` — pty harness used to verify it headlessly (sends keys, captures the screen, prints the latency line)
