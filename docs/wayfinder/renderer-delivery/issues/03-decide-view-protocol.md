# 03 — Decide the view-update protocol and where its seam sits

Type: grilling
Status: open
Blocked by: 01, 02

## Question

What exactly does a renderer receive when the editor changes, and where in the code does that decision live?

Two coupled sub-questions. Settle both with a human in the room — this is the map's central decision.

**A. Payload shape.** Today it is a full snapshot: [`output.put("editorContent", …)`](../../../../src/main/java/com/ple/visur/ModelWasChangedVerticle.java#L28) ships the whole buffer next to ~15 scalars, on every key. Candidates:

- **Full snapshot** — today. Simplest; cost scales with document.
- **Viewport slice** — send `editorContent[canvasStart..canvasEnd]` plus scalars. Cost scales with viewport, not document. Cheapest change that kills the bad scaling; delta-free, so no client state to desync.
- **Delta / patch** — send edits as operations (insert at N, delete N..M). Smallest payload; puts a sequencing and resync burden on every renderer.
- **Cell grid + version** — server sends a viewport cell array and a version; renderer diffs against its copy and repaints changed cells only. Merges payload and repaint decisions.

Also decide a **change kind** so a bare cursor move is distinguishable from a content edit (`cursorOnly` / `contentChanged` / `modeOnly`). Today they are indistinguishable — [`KeyWasPressedVerticle.java:31`](../../../../src/main/java/com/ple/visur/KeyWasPressedVerticle.java#L31) fires the same event for both — which is why a cursor move ships the entire document.

**Changed since this ticket was drafted: the terminal renderer is no longer in the JVM.** It is a separate process ([Decide the terminal client's process boundary and transport](05-decide-process-boundary.md)), which reweights the candidates below. Snapshot and slice are unaffected — both are complete states that a foreign client can hold. **Delta/patch gets materially more expensive**: there is no shared Java object graph to apply an edit against, so every client must implement `insert at N` / `delete N..M` correctly, and the sequencing and resync burden lands on a renderer that is also the least tested. Cell-grid survives, and is now the natural home for the repaint decision, because the client cannot peek at `EditorModelCoupler` to figure out what changed.

**Input envelope is already settled, don't reopen it.** [Terminal input and output from an out-of-JVM client](06-terminal-io-non-jvm.md) confirmed `{"key": <token>}` survives contact with a terminal unchanged (7 fixed names + any single character), with one forward-compatible extension available when first needed: an optional `mods` array, absent meaning empty. Do not redesign the key vocabulary as part of the payload question.

**C. Who wraps lines?** Surfaced by [the terminal client component design](../design/terminal-client-components.md). Today the *browser* wraps — [`x % canvasWidth`](../../../../src/main/resources/static/visur.js#L167-L171) — with `canvasEnd` chosen to cover the visible region. A terminal has no pixel canvas; it has `cols`. So an "unwrapped viewport slice" is not a viewport: either the server sends **pre-wrapped rows** (a row array rather than a character array) and both renderers agree about where a line ends by construction, or the terminal reimplements the canvas's wrapping rule exactly and the two will drift. This is not severable from A — it decides the type of `editorContent` in the payload.

**B. Seam.** Where does "what is the content for this view" live? Candidates, using the `codebase-design` vocabulary:

- **Content-provider interface** — e.g. `ContentView { String content(EditorModelCoupler) }` with a text-file adapter and a directory-listing adapter; `ModelWasChangedVerticle` collapses to one call. Adding a renderer-visible content kind = a new adapter.
- **View state branch in the verticle** — an `if` on an editor-model enum inside [`handleChange`](../../../../src/main/java/com/ple/visur/ModelWasChangedVerticle.java#L19-24). Thinnest; the branch grows once per content kind.
- **Push into the quantum** — rejected in earlier conversation: `Quantum.getBoundaries` already *takes* `editorContent` as a parameter, so a quantum cannot produce it without inverting the dependency.

**The measurements now sharpen A.** At the current 8 KB buffer the payload term is ~0.5 ms of a 17.5 ms round trip — a slice changes nothing a user can feel *today*. At 5 MB the payload term is ~30 ms and the total is 52–122 ms per keystroke, and it grows ~10 ms/MB from there. So the payload decision is about **scaling, not responsiveness**, and the ticket's answer must say which buffer sizes the chosen shape actually wins at. A shape that only helps above 500 KB is still correct — that is the cliff — but it should not be sold as a latency fix.

Bring [Baseline measurements of the current update path](01-baseline-measurements.md)'s numbers and [Survey of incremental rendering and terminal capability baselines](02-terminal-render-survey.md)'s survey. If the baseline says payload dominates, the slice or delta option is indicated; if paint dominates, the cell-grid option is; if neither dominates, argue for the smallest change. Do not pick delta on payload size alone — read the paragraph above first.

The answer records the chosen protocol (with the exact JSON shape), the chosen seam, and the reasoning that ruled out the alternatives — enough that [Consolidate the spec](09-consolidate-spec.md) can write the spec from it without reopening the question.
