# Kilo-shaped spike: Visur in a terminal

Type: prototype
Status: closed
Closed: 2026-10-28
Assignee: pi (agent session, 2026-10-28)

## Question

Does a minimal ANSI renderer driven by the live event bus feel usable — and where does it stop feeling usable?

Raise the fidelity of the terminal discussion by building something concrete and throwaway, in the spirit of Kilo, **in Rust with crossterm** ([Decide the terminal client's implementation language and TUI stack](04-decide-client-language.md)). Keep it to the thin path described in [the component design](../design/terminal-client-components.md): crossterm raw mode + input, hand-rolled grid, no ratatui. This is the first thing written in that language, so the spike doubles as the honest answer to "does this toolchain fight us". Deliberately scoped: if it takes more than a session, it has exceeded its purpose.

**Must work:**

- Connect to the running server the way the browser does ([`MainVerticle`](../../../../src/main/java/com/ple/visur/MainVerticle.java) serves SockJS at `/eventbus`). A stand-in transport is acceptable for a spike — raw WebSocket, or a tiny Java shim — because the transport decision belongs to [Decide the terminal client's shape, transport, and parity](08-decide-terminal-shape.md) and is not settled yet. Note what the stand-in cost, since that is evidence for that ticket.
- Paint the buffer into the terminal viewport, with a cursor that follows `ca` / `cursorQuantumStart` / `cursorQuantumEnd`
- A mode line showing `editorMode` / `editorSubmode` / `span`, mirroring [visur.js:76-107](../../../../src/main/resources/static/visur.js#L76-107)
- `h` `j` `k` `l`, `i`, `Escape`, printable characters, backspace

**Explicitly not needed:** search submodes, spans, mouse, resize, scrolling beyond the viewport, images, panes.

Do it the cheap way: full repaint every event, no diffing, no cell-grid compositor. The question is whether the *feel* is acceptable and the round-trip through the server survives, not whether it is fast — speed is [Decide the view-update protocol and where its seam sits](03-decide-view-protocol.md)'s business.

Watch for, and record:

- Whether a keystroke's round-trip to the server and back is perceptible — this is the first measurement of the terminal's latency floor, and the canvas baseline is the comparison
- Where Escape-as-sequence-prefix causes misbehaviour
- Whether the terminal's own scrollback fights the viewport
- What the terminal does on exit, and on a crash
- Whether the client language's toolchain got in the way (build step, dependency weight, debugging ergonomics) — if it did, say so plainly, because it is cheaper to learn that here than after the spec is written

Link the spike (branch, or a `spike/` folder that is thrown away) from this ticket. The answer records what felt right, what felt wrong, and which of the shape ticket's options the spike argues for.

## Progress notes (agent) — awaiting the human verdict

**Spike built and verified headlessly, 2026-10-28.** Source, launcher and README: [spike/](../spike/README.md). Run with `docs/wayfinder/renderer-delivery/spike/visur`.

Verified in a real pty (`spike/drive.py` drives keys and captures the screen): attaches to the live event bus with **no Java change** (minimal SockJS-over-WebSocket client in Rust), paints the Declaration, wrapping at terminal width, cursor as inverse video, mode line with mode/quantum/span. `h j k l`, `i`, printable chars, `Esc` and `Backspace` all round-trip; a 5-character insert showed up as 5 characters at the cursor.

**Measured, 8 KB buffer, 100×30 terminal, full repaint per key, no diffing: median 2.6–4.4 ms, p95 4.6–8.6 ms keystroke → view rendered.** Compare: 17.5 ms for the same keystroke in the browser, ~1–2 ms for vim. So a naive full-repaint Rust client already beats the canvas client by 4–7×, and vim parity is a matter of tens of milliseconds of tuning, not architecture. Two of those milliseconds were the spike's own poll granularity (a 2 ms socket read timeout measured 4.4 ms median; 200 µs measured 2.6 ms), which is an argument for a real `poll`/`epoll` loop rather than a timed read.

**Three findings that cost nothing to learn here and would have cost the spec more:**

1. **SockJS requires a client ping.** The vert.x eventbus JS client sends `{"type":"ping"}` every ~5 s; without it the session is reaped after exactly 10 s with `c[3000,"Go away!"]`. A client on this transport must implement it — one concrete cost of reusing SockJS instead of a plain socket, for [Decide the terminal client's shape, transport, and parity](08-decide-terminal-shape.md).
2. **Readiness is a handshake, not an open port.** The first `canvasWasChanged` is regularly lost because the verticles are not subscribed when the socket opens. The spike re-sends it every 300 ms until the first view. This is the launcher requirement in [Decide the terminal client's implementation language and TUI stack](04-decide-client-language.md), confirmed empirically.
3. **The client must reimplement the browser's wrapping** — `x % canvasWidth` ([visur.js:167-171](../../../../src/main/resources/static/visur.js#L167-L171)) — merely to place the cursor, because the server sends an unwrapped character array. [Decide the view-update protocol and where its seam sits](03-decide-view-protocol.md) sub-question C, now observed rather than predicted.

Also observed, server-side: `CanvasWasChangedVerticle.handle` prints every line of the document on every resize ([ViewportWasChangedVerticle.java:25-33](../../../../src/main/java/com/ple/visur/ViewportWasChangedVerticle.java#L25-L33)) — 55 log lines per attach at 8 KB, and O(document) per resize. Harmless here; a real cost at 5 MB.

### Not resolved by the above

This ticket is HITL: the question is **what felt right and what felt wrong**, and that is the human's call. Still to answer, with the spike running:

- Is the round trip perceptible? Does typing feel laggy compared to vim?
- Where does `Esc` misbehave (spike has no `DISAMBIGUATE_ESCAPE_CODES`, so it is on crossterm's timeout path)?
- Does the terminal's scrollback fight the viewport (spike uses `?1049`)?
- What does it do on exit and on a crash?
- Does the Rust toolchain get in the way — build step, dependency weight, debugging ergonomics?
- Which shape does this argue for in [Decide the terminal client's shape, transport, and parity](08-decide-terminal-shape.md)?

## Answer (human verdict, recorded 2026-10-28)

The user ran the spike in a real terminal and reported:

- **Typing is very fast — the round trip is not perceptible.** Satisfied with it as-is.
- **`Esc` behaved.** The bug seen in a past browser session did not reproduce in the terminal. (Note: the spike has no `DISAMBIGUATE_ESCAPE_CODES`, so `Esc` is on crossterm's timeout path and still felt fine at 100×30 on a local machine. That weakens the urgency of the kitty push, but does not remove the reason to probe — a slower machine or tmux may still show it.)
- **Scrollback and alt screen caused no trouble.** "Scrolling the window works fine" — the spike's follow-the-cursor row offset was sufficient. This is the first evidence that a row-offset viewport model (not the canvas's pixel `fy`/`canvasStart` pair) is adequate for the terminal.
- **Exiting is clean, with no side effects, and the edits survive.** Worth stating precisely: nothing is written to disk — there is no file I/O in Visur at all — so "changes survive" means the server's in-memory model survives the client, not that the document was saved.
- **No toolchain friction.** Build, dependencies and debugging were unremarkable.

**What the spike argues for, in [Decide the terminal client's shape, transport, and parity](08-decide-terminal-shape.md)'s terms:** a *structured-payload client*, not an ANSI pipe. The client had to lay out the viewport itself (wrap, place the cursor, draw the mode line) and was fast doing it; handing that work to the server would have bought nothing and cost the canvas renderer its structure. It says nothing about transport beyond "SockJS-over-WebSocket works but demands a client ping", and the 2.6–4.4 ms measured through it is already good enough that transport choice is a *simplicity* question rather than a latency one.

### Two feature gaps found while doing this (recorded on the map as next-effort work)

- **Span has a key, but span is binary.** `s` pushes the span submode and a digit sets it — `s 0` gives a thin cursor, `s 1` a one-quantum block. [`SetSpanOp`](../../../../src/main/java/com/ple/visur/SetSpanOp.java) clamps any positive value to 1 (`spanToSet > 0 ? 1 : 0`), so `s 3` silently does nothing different from `s 1`. Spanning several quantums is not implementable today, contrary to the README's "Spans 0-1" being a range of *counts*.
- **There is no way to quit.** No quit/exit operator exists anywhere; `CommandStateService.exitCommandState` only leaves the `;` command line, and that line is itself unreachable by keypress. The client owns quitting (raw mode makes `Ctrl-C` a byte), so a terminal client must bind it — but "quit the editor" as a Visur concept does not exist to bind to.
