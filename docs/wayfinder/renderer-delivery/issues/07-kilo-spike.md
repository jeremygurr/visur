# Kilo-shaped spike: Visur in a terminal

Type: prototype
Status: open
Blocked by: 06

## Question

Does a minimal ANSI renderer driven by the live event bus feel usable — and where does it stop feeling usable?

Raise the fidelity of the terminal discussion by building something concrete and throwaway, in the spirit of Kilo, **in the language chosen in [Decide the terminal client's implementation language and TUI stack](04-decide-client-language.md)**. This is the first thing written in that language, so the spike doubles as the honest answer to "does this toolchain fight us". Deliberately scoped: if it takes more than a session, it has exceeded its purpose.

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
