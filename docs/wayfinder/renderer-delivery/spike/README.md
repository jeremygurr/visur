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

It speaks plain JSON to the server's `/ws` socket ([frame shape](../spec.md)), so it needs a server that has that
route. It originally spoke SockJS to `/eventbus`, needing no Java change at all; that was replaced when the route
existed, because the SockJS ritual — framing, a 5 s ping, a re-sent declaration — was three browser-specific
obligations a terminal client had no business carrying.

## What works

`h` `j` `k` `l` · `i` · `Esc` · printable characters · `Backspace` · arrows · `Ctrl-C` / `Ctrl-Q` to quit.

Mode line shows mode/submode, quantum, span, and the last measured keystroke→render latency. On exit it prints median and p95.

## Deliberately not here

Diffing, damage regions, scrollback beyond the viewport, mouse, resize handling (the size is read once at startup), `?2026`, truecolor, command state, search submodes, paste, and any Rust test coverage. `ratatui` is not used — that is the decision, not an omission.

## Three things it taught us, cheaper than the spec would have

1. **The SockJS session must be pinged.** The vert.x eventbus JS client sends `{"type":"ping"}` about every 5 s; without it the server reaps the session after exactly **10 s** with `c[3000,"Go away!"]`. This is why SockJS was rejected for the terminal (ticket 08) and why `/ws` exists (ticket 04): on a plain socket there is no session to keep alive. *No longer applies — the spike uses `/ws`.*
2. **Readiness is a handshake, not an open port.** The first declaration was regularly lost because the verticles are not subscribed yet when the socket opens. *Fixed at the source 2026-11-01*: the server now opens its port only after they are deployed, so the spike declares once and the 300 ms resend loop is deleted.
3. **The client must reimplement the browser's wrapping.** `x % canvasWidth` was needed just to know where to put the cursor, because the server sent an unwrapped character array. This is the wrapping hole in [the component design](../design/terminal-client-components.md), now observed rather than predicted.
   **Superseded 2026-11-01**: this finding is *why* the protocol changed. The server now sends pre-wrapped `rows` and `cursorRow`/`cursorCol`, so the spike's `Layout` pass — the wrapping, the absolute-index table and `cursor_cell` — is deleted ([terminal-build ticket 03](../../terminal-build/issues/03-switch-payload.md)). It was the last client reimplementing the wrap.

## Files

- `visur-term/src/main.rs` — the whole client: `/ws` frames, view parsing, paint, key adapter
- `visur` — the launcher

Its Python pty harness (`drive.py`) is gone: the real client's end-to-end check is a Rust test that does the same
job — open a pty, set its size, type keys, read the escape stream back — so no second language is needed to
verify a client ([terminal/tests/pty.rs](../../../../terminal/tests/pty.rs)).
