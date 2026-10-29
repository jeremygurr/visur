# 01 — Baseline measurements of the current update path

Type: task
Status: open

## Question

What does one keystroke actually cost today, and which half of the cost dominates — the payload or the paint?

Nothing can be decided about optimizing screen updates until this number exists. The current path, for reference:

- [KeyWasPressedVerticle.java:31](../../../../src/main/java/com/ple/visur/KeyWasPressedVerticle.java#L31) — every key, including a bare cursor move, becomes `modelWasChanged`
- [ModelWasChangedVerticle.java:20,28](../../../../src/main/java/com/ple/visur/ModelWasChangedVerticle.java#L19-28) — the whole buffer is read and serialized onto the wire
- [visur.js:72](../../../../src/main/resources/static/visur.js#L72) — `ctx.clearRect` over the entire canvas
- [visur.js:136-160](../../../../src/main/resources/static/visur.js#L136-160) — one `fillText` per character cell in the viewport

Record, on macOS, for at least these combinations:

| Buffer size | Viewport |
|---|---|
| the current init buffer (~8 KB Declaration) | ~1470×900 window |
| ~500 KB | ~1470×900 window |
| ~5 MB | ~1470×900 window |
| ~500 KB | full-screen / large display |

For each cell of that table, capture:

1. **Payload bytes per keystroke** — the serialized `viewWasChanged` JSON size.
2. **End-to-end latency** — keydown to last painted pixel. Same-process `performance.now()` marks around the `viewWasChanged` handler are sufficient for a baseline.
3. **`fillText` call count per frame**, and `clearRect` + `drawCanvas` wall time.
4. **Cursor-only vs content-changing keys measured separately** (e.g. `l` vs typing a character in insert mode) — these are the two cases a protocol change would treat differently.

Constraints: do not restructure the renderer. A measurement hook is fine; a rewrite is not. Throw the harness away afterward, but record the method in the answer so it can be repeated.

The answer records the numbers, the method, and an explicit verdict on whether payload or paint dominates — because [Decide the view-update protocol and where its seam sits](03-decide-view-protocol.md) is blocked on that verdict.
