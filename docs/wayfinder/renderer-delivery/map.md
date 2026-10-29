# Map: renderer-delivery

## Destination

A decided approach and a spec for how Visur's view reaches a renderer, covering both current goals as one effort:

1. **Canvas updates stop scaling with document size.** Today every keystroke ships the whole buffer and repaints the whole viewport (see [visur.js:72](../../../src/main/resources/static/visur.js#L72), [ModelWasChangedVerticle.java:20-28](../../../src/main/java/com/ple/visur/ModelWasChangedVerticle.java#L19-28)).
2. **Visur opens from a terminal**, not only a browser tab.

Both are answered by one artifact — a renderer-neutral view-update protocol plus the seam where view production lives — which is why they share this map. Reaching the destination means `spec.md` exists and an implementer needs to ask nothing further.

**The map closes at the spec.** The build is a second effort, charted fresh once the spec is signed off — see Out of scope.

## Notes

- **Tracker**: local markdown under `docs/wayfinder/renderer-delivery/`, committed to git. It was moved here from an untracked `.scratch/` on 2026-10-28. `gh` is authenticated against `jeremygurr/visur` if this ever migrates to GitHub issues; it does not need to.
- **Skills every session should consult**: `grilling` + `domain-modeling` (all HITL tickets), `research` (all research tickets), `codebase-design` (seam / interface / depth / adapter vocabulary), `prototype` (the spike).
- **Read before any ticket** — the current renderer path, in order:
  - [KeyWasPressedVerticle.java](../../../src/main/java/com/ple/visur/KeyWasPressedVerticle.java) — every key becomes `modelWasChanged`
  - [ModelWasChangedVerticle.java](../../../src/main/java/com/ple/visur/ModelWasChangedVerticle.java) — full buffer serialized per key
  - [CanvasWasChangedVerticle.java](../../../src/main/java/com/ple/visur/CanvasWasChangedVerticle.java) — canvas dimensions, plus a leftover debug loop
  - [visur.js](../../../src/main/resources/static/visur.js) — clear whole canvas, repaint every viewport cell
  - [EditorModelCoupler.java](../../../src/main/java/com/ple/visur/EditorModelCoupler.java) — all editor state, server-side
- **Facts already in the build, not yet used**: `vertx-shell` and `vertx-tcp-eventbus-bridge` are existing dependencies ([build.gradle.kts:30-34](../../../build.gradle.kts#L30-34)). Neither is referenced by any source file. They are the two cheapest starting points for a terminal client and for reaching the event bus without SockJS — but see the settled decision below before reaching for either.
- **Settled 2026-10-28, do not reopen without ruling the ticket out of scope**: the terminal client is **its own process outside the JVM**. See [Decide the terminal client's process boundary and transport](issues/05-decide-process-boundary.md). Every ticket downstream assumes this.
- **Standing preference: measure before optimizing.** No dirty-rect or diffing work is chartered until the baseline ticket produces a number showing *which* half (payload or paint) dominates.
- **Standing preference: the client stays dumb.** `EditorModelCoupler` holds all editor state; renderers only paint what they are told. Any design that moves editor logic into a renderer is out of bounds. This is now enforced by architecture rather than by discipline: an out-of-JVM client *cannot* reach `EditorModelCoupler`, which is the point.
- **Evidence quality caveat, 2026-10-28**: [Survey of incremental rendering and terminal capability baselines](issues/02-terminal-render-survey.md) was produced with search but no page-fetch, so its upstream-source claims (Neovim `grid_line` trailing-cell elision, tmux's exact redraw-trigger list) are architecture-level with files cited but not opened. Treat them as strong leads, not settled facts, and confirm against source before the spec encodes them. The survey's own §0 and §5 say the same.
- **Predecessor effort**: filesystem navigation was the previous destination and is shelved, not rejected — the user redrew the destination on 2026-10-21. See Out of scope.
- **Prior leaning, not a decision**: this conversation already leaned toward a content-provider interface at the seam (`output = emc.getView().content(emc)` rather than an `if` inside `ModelWasChangedVerticle`), and toward a viewport slice plus an explicit change kind. [Decide the view-update protocol and where its seam sits](issues/03-decide-view-protocol.md) owns that decision; do not treat the leaning as settled.

## Decisions so far

<!-- one line per resolved ticket: gist, then link to the ticket holding the detail -->

- [Decide the terminal client's process boundary and transport](issues/05-decide-process-boundary.md): the terminal client is a separate, non-JVM process that speaks to the server only over the network — chosen because a protocol that a foreign runtime must satisfy cannot secretly cheat by reading `EditorModelCoupler`. Transport deliberately left open to the shape ticket.
- [Survey of incremental rendering and terminal capability baselines](issues/02-terminal-render-survey.md): paint is viewport-bounded and cheap — a full viewport repaint per keystroke is fine at ≤200×60 with `DEC 2026`, `1049`, and hidden cursor — while Neovim's row-run `grid_line`/`grid_scroll`/cursor vocabulary, a distinct paste message, and a startup probe of `2026` + truecolour are the shape the payload and client should take; detail in [the survey](../research/terminal-render-survey.md).

## Not yet specified

Fog — in scope, real, but not sharp enough to ticket yet. Each patch graduates when the frontier reaches it.

- **Whether the repaint half is worth touching at all.** Hangs entirely on the baseline ticket's numbers. Full repaint is bounded by viewport, so it may already be fine; dirty regions may be pure complexity.
- **Whether view production and repaint are one seam or two.** The protocol ticket decides payload; repaint strategy may be a separate seam in the renderer, or the same one.
- **What wire protocol the out-of-JVM client actually speaks.** The `vertx-tcp-eventbus-bridge` gives a binary event-bus wire format for free, but requires the client to reimplement Vert.x framing and heartbeats; a plain JSON-over-WebSocket or Unix-socket endpoint sidesteps that at the cost of a second listener in `MainVerticle`. This leans on the protocol decision — if the view payload is being redesigned anyway, carrying it inside Vert.x's frame format may be more coupling than it is worth. Graduates once the protocol ticket lands.
- **Key-token vocabulary on the wire.** Today the browser sends `event.key` strings ([visur.js:132-134](../../../src/main/resources/static/visur.js#L132-L134)) straight into [`KeyPressed.from`](../../../src/main/java/com/ple/visur/KeyPressed.java). A terminal produces bytes, not names. Whether the client normalises to today's vocabulary (cheap, keeps `Keymap` untouched) or the vocabulary is redesigned (honest, touches `Keymap`/`KeymapMap`/`InitializerService`) is not yet sharp.
- **How the canvas's scroll model maps to a terminal.** Canvas tracks `canvasStart` / `canvasEnd` / `fy` ([EditorModelCoupler.java](../../../src/main/java/com/ple/visur/EditorModelCoupler.java)); the terminal has its own scrollback and alternate-screen semantics. A row-offset model may be the shared abstraction, or the two may diverge.
- **Selection and span rendering parity.** The canvas paints the cursor-quantum as a gold `fillRect` block ([visur.js:199-209](../../../src/main/resources/static/visur.js#L199-L209)); a terminal needs an SGR/inverse-video equivalent, and multi-cell spans over wrapped lines.
- **Whether command-state and mode-line rendering move into the renderer.** Today they are payload scalars (`isInCommandState`, `commandStateContent`, `commandCursor`) plus `innerHTML` writes in the client ([visur.js:76-107](../../../src/main/resources/static/visur.js#L76-L107)). The terminal has no DOM to write into.
- **Resize handling parity.** `canvasWasChanged` carries dimensions from the browser to the server; a terminal delivers `SIGWINCH`. Whether one `viewportWasChanged` event serves both.
- **Simultaneous attach (browser and terminal at once).** Currently plausible for free because all state is server-side; may be a beta feature or may be multi-user territory.
- **What "optimized" means numerically.** A latency budget (keystroke → last pixel / last cell) that a solution has to hit, and at which buffer sizes.
- **Whether the terminal needs mouse, images, or multiple panes** to count as usable for daily work, or whether vim-parity keys alone suffice.
- **How a second toolchain ships.** `./gradlew run` must stay the one command that starts Visur; whether that shells out to a client binary, or the client is fetched/built separately, is unexamined.

## Out of scope

Work consciously ruled out of this effort. Never graduates; returns only if the destination is redrawn.

- **Implementing the protocol, the seam change, or the terminal client.** The map ends at `spec.md`; the build is a new effort with its own destination. Ruled out 2026-10-28 so this map stays a decision effort.
- **Filesystem navigation** (`FileQuantum`, `DirectoryQuantum`, `NormalView`/`FilesystemView`, file read/write) — the previous destination, deferred 2026-10-21. Its own notes survive in the repo [todo](../../../todo). Revisit as a fresh effort once this map closes.
- **Multi-user / concurrent editing** (README future feature 2).
- **Type-sensitive editing of objects rather than text** (README future feature 1).
- **Remote / SSH attach and distributed sessions.** The client connects to a server on the same machine for beta.
- **A GUI / native-window client** (README future feature 4 in its "graphical window" sense). The terminal renderer covers the "interface independent" goal for beta.
- **Any plugin system.** Visur is customized by changing its source, per the project's own stance.
