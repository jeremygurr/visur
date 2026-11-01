# Map: renderer-delivery

**Status: closed 2026-10-28 — destination reached.** All nine tickets resolved; the artifact is [spec.md](spec.md). The build is a separate effort.

## Destination

A decided approach and a spec for how Visur's view reaches a renderer, covering both current goals as one effort:

1. **Canvas updates stop scaling with document size.** Today every keystroke ships the whole buffer and repaints the whole viewport (see [visur.js:72](../../../src/main/resources/static/visur.js#L72), [ModelWasChangedVerticle.java:20-28](../../../src/main/java/com/ple/visur/ModelWasChangedVerticle.java#L19-28)).
2. **Visur opens from a terminal**, not only a browser tab.

Both are answered by one artifact — a renderer-neutral view-update protocol plus the seam where view production lives — which is why they share this map. Reaching the destination means `spec.md` exists and an implementer needs to ask nothing further.

**The two goals have different bars, settled 2026-10-28.** Goal 1 is *bounded*, not vim-parity: the canvas client must stop paying for the document and stay inside the frame budget, and it already paints in 1.6 ms, so its remaining work is entirely the payload. Goal 2 is where **vim parity (~1–2 ms keystroke → screen) is the target**, which is achievable for a client outside the browser because the measured ~17.5 ms floor is on the SockJS/browser delivery path, not in Visur's own work.

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
- **Per-renderer latency bar**: canvas = optimized (viewport-bounded payload, paint inside the frame budget, no vim-parity claim); terminal = vim parity. Do not let a ticket argue one bar for both.
- **Measured, 2026-10-28, do not re-derive**: paint is 1.6–2.4 ms and viewport-bounded; a payload-independent ~17.5 ms round-trip floor dominates below 500 KB; payload adds ~10 ms/MB above that. Detail in [Baseline measurements of the current update path](issues/01-baseline-measurements.md). Consequence: dirty-region rendering is off the table. The full-buffer cost is paid **twice** — `emc.getEditorContent()` rebuilds a whole-document `String` and `toJson()` re-encodes it, both inside the 5.5 ms measured at 5 MB — so the payload fix removes a server-side cost too, not just wire bytes.
- **Standing preference: the client stays dumb.** `EditorModelCoupler` holds all editor state; renderers only paint what they are told. Any design that moves editor logic into a renderer is out of bounds. This is now enforced by architecture rather than by discipline: an out-of-JVM client *cannot* reach `EditorModelCoupler`, which is the point.
- **Evidence quality caveat, 2026-10-28**: [Survey of incremental rendering and terminal capability baselines](issues/02-terminal-render-survey.md) was produced with search but no page-fetch, so its upstream-source claims (Neovim `grid_line` trailing-cell elision, tmux's exact redraw-trigger list) are architecture-level with files cited but not opened. Treat them as strong leads, not settled facts, and confirm against source before the spec encodes them. The survey's own §0 and §5 say the same. The same caveat applies to [Terminal input and output from an out-of-JVM client](issues/06-terminal-io-non-jvm.md): its Visur-side facts are read from source, but crossterm's private ESC-timeout constant and its exact macOS raw-mode flag set are flagged unverified there, with the check that settles each.
- **Two prerequisites recorded for later efforts, both discovered here and both bigger than this map**: (1) **session-scoped viewport state** — `ServiceHolder.editorModelCoupler` is a singleton and the viewport lives inside it, so multi-interface and multi-user both require per-subscriber state; the payload was designed to survive that transition. (2) **content-parameterising the operator/quantum stack** — sixteen call sites read `emc.getEditorContent()` directly, which is why there is no `ContentView` seam; a second content kind needs them changed first.
- **Predecessor effort**: filesystem navigation was the previous destination and is shelved, not rejected — the user redrew the destination on 2026-10-21. See Out of scope.
- **Prior leaning, not a decision**: this conversation already leaned toward a content-provider interface at the seam (`output = emc.getView().content(emc)` rather than an `if` inside `ModelWasChangedVerticle`), and toward a viewport slice plus an explicit change kind. [Decide the view-update protocol and where its seam sits](issues/03-decide-view-protocol.md) owns that decision; do not treat the leaning as settled.

## Decisions so far

<!-- one line per resolved ticket: gist, then link to the ticket holding the detail -->

- [Decide the terminal client's process boundary and transport](issues/05-decide-process-boundary.md): the terminal client is a separate, non-JVM process that speaks to the server only over the network — chosen because a protocol that a foreign runtime must satisfy cannot secretly cheat by reading `EditorModelCoupler`. Transport deliberately left open to the shape ticket.
- [Baseline measurements of the current update path](issues/01-baseline-measurements.md): at the current 8 KB buffer a **payload-independent ~17.5 ms round-trip floor** is the latency; payload adds ~10 ms/MB and blows up above ~500 KB (32 ms at 5 MB); **paint is 1.6–2.4 ms and flat across buffer sizes**, so dirty-region work is dead. The fix for payload is a scaling fix, not a responsiveness fix — vim parity needs the floor gone.
- [Decide the terminal client's implementation language and TUI stack](issues/04-decide-client-language.md): **Rust + crossterm, minimal tier** — crossterm owns only raw mode/input/`SIGWINCH`; the client owns the cell grid, the damage model, every escape byte, and the paint policy. Design and per-stage budget in [the component doc](design/terminal-client-components.md). `ratatui`/`tcell` rejected for owning the compositor; Zig+libvaxis rejected on toolchain cost.
- [Survey of incremental rendering and terminal capability baselines](issues/02-terminal-render-survey.md): paint is viewport-bounded and cheap — a full viewport repaint per keystroke is fine at ≤200×60 with `DEC 2026`, `1049`, and hidden cursor — while Neovim's row-run `grid_line`/`grid_scroll`/cursor vocabulary, a distinct paste message, and a startup probe of `2026` + truecolour are the shape the payload and client should take; detail in [the survey](research/terminal-render-survey.md).
- [Consolidate the spec](issues/09-consolidate-spec.md): [spec.md](spec.md) — ten sections, written from the nine tickets, with four half-stated points made explicit (per-direction addresses, the wrap-alignment invariant, empty-rows clamping, chrome excluded from the declared grid). Five prerequisites and the non-goals are listed rather than deferred.
- [Decide the terminal client's shape, transport, and parity](issues/08-decide-terminal-shape.md): **structured-payload client** (ANSI pipe overruled), speaking **plain JSON over a new localhost `/ws` WebSocket** — SockJS rejected as a browser protocol we would maintain forever in Rust; the client speaks a `Transport` trait so this is one file to reverse. **Scrolling is server-mediated**: a renderer only holds its viewport's rows and cannot scroll past them. Parity split into must/should/won't, with `Ctrl-C`/`Ctrl-Q` as the quit binding.
- [Decide the view-update protocol and where its seam sits](issues/03-decide-view-protocol.md): the payload is **viewport-bounded, server-wrapped rows with per-row absolute origins** (`rows: [{start, text}]` + `origin` + `full` + server-derived `cursorRow`/`cursorCol`), `editorContent`/`ca`/`canvasEnd` dropped; `Viewport` lays out (pure, no `emc`), `View` projects, the verticle is a pipe; **one** inbound `viewportWasChanged` with zero branches and **one** payload format with **N viewports** — interfaces differ in which viewport they get and how they paint, never in what is true.
- [Kilo-shaped spike: Visur in a terminal](issues/07-kilo-spike.md): a 374-line Rust + crossterm client over SockJS paints and edits the real document at **2.6–4.4 ms median keystroke → render** (browser: 17.5 ms) with a naive full repaint — so **paint is confirmed not the problem and vim parity is available**. Typing felt fast, `Esc` was fine, scrollback/alt-screen caused no trouble. Argues for a **structured-payload client**, not an ANSI pipe. Code in [spike/](spike/README.md).
- [Terminal input and output from an out-of-JVM client](issues/06-terminal-io-non-jvm.md): the wire key vocabulary is **7 fixed names + "any single character"** and needs no redesign — so the client **normalises** to it — but the adapter must drop `KeyModifiers != NONE` (or `Ctrl-d`/`Alt-d` arrive as the navigate binding `d`), and raw mode makes **Ctrl-C a byte rather than `SIGINT`**, so quitting is the client's job; complete byte→token table in [the mapping research](research/terminal-input-mapping.md), which also settles this map's "key-token vocabulary on the wire" fog.

## Not yet specified

Nothing left. Every question that was fog has been settled by a ticket — the repaint half, the numeric budget, the wire protocol, the scroll model, span rendering, simultaneous attach (now a named prerequisite), the launcher's ownership rule, and the packaging of the second toolchain (which is spec-writable, so it belongs to [Consolidate the spec](issues/09-consolidate-spec.md)). The way to the destination is clear; one ticket remains.

- **Key-token vocabulary on the wire.** Today the browser sends `event.key` strings ([visur.js:132-134](../../../src/main/resources/static/visur.js#L132-L134)) straight into [`KeyPressed.from`](../../../src/main/java/com/ple/visur/KeyPressed.java). A terminal produces bytes, not names. Whether the client normalises to today's vocabulary (cheap, keeps `Keymap` untouched) or the vocabulary is redesigned (honest, touches `Keymap`/`KeymapMap`/`InitializerService`) is not yet sharp. **Resolved 2026-10-28 — normalise; detail in [Terminal input and output from an out-of-JVM client](issues/06-terminal-io-non-jvm.md).** Left here only until the fog is swept.
- **Simultaneous attach, now with its prerequisite named.** It is not free: viewport state is process-wide, so two clients overwrite each other's `canvasWidth` and both render for the last caller. Needs **session-scoped viewport state**, which is the same change as multi-user editing — out of scope here, but the payload was designed to survive it.

## Out of scope

Work consciously ruled out of this effort. Never graduates; returns only if the destination is redrawn.

- **Visur feature gaps and stress cases surfaced by the renderer work** — all app features, not renderer ones, and all deferred to the effort that builds this spec:
  - **No quit.** No quit/exit operator exists; `CommandStateService.exitCommandState` only leaves the `;` line. A client must bind quitting, but there is nothing to bind it to yet.
  - **Span is binary.** `s` + digit is the key ([`SetSpanOp`](../../../src/main/java/com/ple/visur/SetSpanOp.java) clamps to 0/1), so a multi-quantum span cannot be expressed.
  - **No copy/paste**, and no paste message in the protocol (only bracketed paste at the terminal).
  - **No document-jump keys** — no `gg`/`G` equivalent, so top↔bottom is unreachable in one key.
  - **No search-and-replace** — search (`f`/`F`/`n`/`N`) exists; substitution does not, and replace mode is overwrite-typing.
  - **No file I/O at all**, so "keeping changes" means the server's memory, not a saved document.
  These are also the **benchmark set** for the scaling question: they are the operations that will expose the full-buffer payload on large documents, which is why they matter here even though they are not built here.
- **Implementing the protocol, the seam change, or the terminal client.** The map ends at `spec.md`; the build is a new effort with its own destination. Ruled out 2026-10-28 so this map stays a decision effort.
- **Filesystem navigation** (`FileQuantum`, `DirectoryQuantum`, `NormalView`/`FilesystemView`, file read/write) — the previous destination, deferred 2026-10-21. Its own notes survive in the repo [todo](../../../todo). Revisit as a fresh effort once this map closes.
- **Multi-user / concurrent editing** (README future feature 2).
- **Type-sensitive editing of objects rather than text** (README future feature 1).
- **Remote / SSH attach and distributed sessions.** The client connects to a server on the same machine for beta.
- **A GUI / native-window client** (README future feature 4 in its "graphical window" sense). The terminal renderer covers the "interface independent" goal for beta.
- **Any plugin system.** Visur is customized by changing its source, per the project's own stance.
