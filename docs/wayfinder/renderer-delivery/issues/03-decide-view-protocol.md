# 03 — Decide the view-update protocol and where its seam sits

Type: grilling
Status: closed
Closed: 2026-10-28
Assignee: pi (agent session, 2026-10-28)

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

## Answer

Decided with the user across two rounds, 2026-10-28.

### A. Payload: one renderer-neutral format, viewport-bounded, rows with origins

```jsonc
{
  "full": false,                       // true => discard caches, the viewport moved or geometry changed
  "origin": 1200,                      // absolute char index of rows[0].start; meaningful when rows is empty
  "rows": [                            // ONE PER SCREEN ROW, ALREADY WRAPPED BY THE SERVER
    { "start": 1200, "text": "…" },    // start = absolute index of text[0]
    { "start": 1293, "text": "…" }
  ],
  "span": 1,
  "cursorQuantumStart": 1240,          // absolute char indices, unchanged from today
  "cursorQuantumEnd": 1241,
  "cursorRow": 3,                      // index INTO rows, not a document row
  "cursorCol": 47,
  "isAtQuantumStart": true,
  "cursorQuantum": "character",
  "scopeQuantum": "character",
  "editorMode": "navigate",
  "editorSubmode": "navigate",
  "isInCommandState": false,
  "commandStateContent": "",
  "commandCursor": 0,
  "searchTarget": ""                   // always present now; empty when not searching
}
```

Field audit against actual consumers, not the producer:

| Field | Fate | Reason |
|---|---|---|
| `editorContent` | **replaced by `rows`** | the whole point of the ticket |
| `ca` | **dropped** | assigned at [visur.js:50](../../../../src/main/resources/static/visur.js#L50), never read anywhere |
| `canvasStart` | **renamed `origin`** | same value, honest name |
| `canvasEnd` | **dropped** | derivable from the last row |
| `span`, `cursorQuantumStart/End`, `isAtQuantumStart`, `cursorQuantum`, `scopeQuantum` | kept | read by both renderers; needed for highlighting |
| `editorMode`, `editorSubmode`, `isInCommandState`, `commandStateContent`, `commandCursor`, `searchTarget` | kept | read by both renderers; `searchTarget` becomes unconditional |
| `rows`, `origin`, `full`, `cursorRow`, `cursorCol` | **added** | viewport-bounded payload, one repaint hint, no layout in renderers |

**Why rows rather than a character slice.** [`getCanvasEnd()`](../../../../src/main/java/com/ple/visur/EditorModelCoupler.java#L91-L108) already computes the visible region in wrapped-row units, and [`decrementCanvasStart`](../../../../src/main/java/com/ple/visur/EditorModelCoupler.java#L354-L379) keeps `canvasStart` walk-aligned to `canvasWidth` within a line — so re-slicing from `origin` reproduces the browser's global `x % canvasWidth` wrap exactly. A character slice would force every renderer to re-implement wrapping to place a cursor, which the spike had to do and got right only by copying [visur.js:167-171](../../../../src/main/resources/static/visur.js#L167-L171).

**Why not delta.** The client is out of the JVM, so every renderer would have to apply insert/delete correctly and a dropped message would corrupt the screen instead of costing one repaint. Row runs are a *repaint* vocabulary, not a payload one.

**Why not a cell grid.** It would put colour in the protocol before `VisurColor` is wired up. Characters travel; renderers paint.

**Why rows carry their origin.** Any absolute index (`cursorQuantumStart/End`, future selections) maps to (row, col) by a scan of `rows`, with no wrap table on either side and no duplicated layout math. `cursorRow`/`cursorCol` are derived server-side for the same reason. Because the payload is a *complete* viewport state, ordering only matters for viewport jumps, and `full: true` is the signal to discard caches rather than diff.

### B. Seam: `Viewport` lays out, `View` projects, the verticle is a pipe

| Piece | One task | Knows about |
|---|---|---|
| `Viewport` | lay out — visible char range + width ⇒ rows with origins | content string, width, first index. **Not `emc`** |
| `View` | project editor state into the payload | the `emc` getters it must read; delegates layout |
| `ModelWasChangedVerticle` | pipe | nothing |

This is the single-responsibility split the user asked for: `View.render(emc)` as originally proposed would have read ~16 `emc` accessors **and** done wrapping — two tasks. `Viewport` being a pure function of plain values also makes it the first unit-testable piece in this area.

`ModelWasChangedVerticle` currently holds `View view` as a mutable field and rewrites it per event ([ModelWasChangedVerticle.java:8](../../../../src/main/java/com/ple/visur/ModelWasChangedVerticle.java#L8)) — shared mutable state across messages on a shared event loop. The new shape is `View.of(emc).render()`: no field, no reuse.

The `searchTarget` construction moves out of the serializer into `emc.getSearchPrompt()`; appending a literal `"_"` to fake a cursor is not a serialization concern.

**No `ContentView` interface.** It was considered seriously and rejected on evidence, not scope: `emc.getEditorContent()` is called directly by sixteen operators and quantums ([InsertCharOp:10](../../../../src/main/java/com/ple/visur/InsertCharOp.java#L10), [DeleteCursorQuantumOp:15](../../../../src/main/java/com/ple/visur/DeleteCursorQuantumOp.java#L15), [ReplaceCharOp:8](../../../../src/main/java/com/ple/visur/ReplaceCharOp.java#L8), [RelativeMoveOp:10](../../../../src/main/java/com/ple/visur/RelativeMoveOp.java#L10), [SearchForwardOp:16](../../../../src/main/java/com/ple/visur/SearchForwardOp.java#L16), [RegexQuantum](../../../../src/main/java/com/ple/visur/RegexQuantum.java#L59) ×5, [LineQuantum:84](../../../../src/main/java/com/ple/visur/LineQuantum.java#L84)), so an interface supplying a *different* content would produce a view no editing command could act on. Nothing varies at that point today, and an interface there would read as a supported extension point that is not one. **The seam is at View/EditorModel, not at content.**

Instead: `View` reads the content in **exactly one named expression**, marked as the place a second content kind plugs in, and the spec states the prerequisite in finding 5 below.

### C. One inbound event, one payload format, N viewports

- **Inbound** `canvasWasChanged` ⇒ **`viewportWasChanged`**, body `{cols, rows}` in cells. One address, one verticle, **zero branches**: every renderer knows its own cell size, so the body is interface-neutral. Mirrors the browser, which already divides pixels by cell size ([visur.js:110-113](../../../../src/main/resources/static/visur.js#L110-L113)). A per-interface verticle was considered and rejected: it would run identical code against the identical global `emc.putCanvasWidth`, and would obscure the real constraint below.
- **Outbound** `viewWasChanged` keeps one renderer-neutral format. **Interfaces differ in *which viewport* they are sent and *how they paint*, not in what is true.** Forking the payload per interface would mean N serializers that drift and every new editor-state field added N times — the case-scenario explosion the split was meant to avoid, merely moved into the type system. A renderer needing fundamentally different data (e.g. a document-wide minimap) gets a *different message*, not a different format of this one.
- The rule for this class of question: **split when behaviour differs, not when callers differ.**

### D. Migration order (no dual format)

1. Extract `Viewport` + `View` against the **current** payload. Pure refactor, no wire change, verifiable by running the browser client.
2. Switch the wire — add `rows`/`origin`/`full`/`cursorRow`/`cursorCol`, drop `editorContent`/`ca`/`canvasEnd` — **and update `visur.js` in the same change**. The canvas renderer then deletes its wrap math and paints rows.
3. Rename the **inbound** event to `viewportWasChanged` ([`BusEvent`](../../../../src/main/java/com/ple/visur/BusEvent.java), `MainVerticle` inbound permit, `visur.js`, the spike). The outbound stays `viewWasChanged` — *correction added while writing [the spec](../spec.md) §2*: an earlier draft of this line said "both ways", which would have given inbound and outbound the same address and made a bridge echo each client's size message to every client.
4. `emc.getSearchPrompt()`, deleting the `"_"` hack.

Steps 2 and 3 go together so clients update once. No compatibility window: one deployed client, in this repo; the spike breaks until it is updated in about ten lines.

### What a renderer may and may not do

- MAY assume every message is a complete viewport state; MUST tolerate unknown fields.
- MUST treat `full: true` as "discard cached rows, do not diff".
- MUST NOT derive editor behaviour, apply edits, or hold editor state. The protocol enforces this structurally: a renderer outside the JVM *cannot* reach `EditorModelCoupler`.
- MUST NOT re-wrap. Row boundaries are the server's word.

### Findings that outlive this ticket (recorded on the map)

1. **Session-scoped viewport state is a prerequisite** for multi-interface *and* multi-user. `ServiceHolder.editorModelCoupler` is a process-wide singleton and `canvasWidth`/`canvasHeight`/`canvasStart`/`fy` live in that one model ([EditorModelKey](../../../../src/main/java/com/ple/visur/EditorModelKey.java#L4-L35)), so today two clients overwrite each other's viewport and both are rendered for the last caller. `N viewports` requires session scoping, not new verticles or new payloads.
2. **The payload must survive that transition**, which is why it is self-describing and viewport-bounded.
3. **Content-parameterising the operator/quantum stack** is the prerequisite for a second content kind (see B).
4. **`CanvasWasChangedVerticle.handle` has a leftover debug loop** that prints every line of the document on every resize ([ViewportWasChangedVerticle.java:25-33](../../../../src/main/java/com/ple/visur/ViewportWasChangedVerticle.java#L25-L33)) — O(document) per resize. Delete it during step 3.
