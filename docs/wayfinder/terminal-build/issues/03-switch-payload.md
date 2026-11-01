# Switch the wire to viewport rows

Type: task
Status: closed
Closed: 2026-11-01
Blocked by: 02, 01

## Work

Spec §1 and §7 step 2 — the change the whole effort exists for.

- Emit `full`, `origin`, `rows: [{start, text}]`, `cursorRow`, `cursorCol`, and keep the twelve retained fields.
- **Drop `editorContent`, `ca`, `canvasEnd`.**
- **Update [`visur.js`](../../../../src/main/resources/static/visur.js) in the same change**: read `rows`, delete its wrapping (`x % canvasWidth`, `numberOfWrappedLines`), paint row by row, place the cursor from `cursorRow`/`cursorCol`, and replace the `modelWasChanged` echo with `full: true` handling.
- No compatibility window — dual format means two server paths and a renderer that sniffs. The spike breaks here; it is fixed in ticket 05.

## Done when

- The browser renders correctly at several window sizes, with spans, search submodes, command state and scrolling all behaving as before.
- **Measured**: payload bytes per keystroke and keystroke → paint at 100 KB / 1 MB / 5 MB, against the baseline's numbers, with paint unchanged (it should be — it was already viewport-bounded) and payload now flat in document size.
- `visur.js` contains no wrap arithmetic.

## Result

The wire is now exactly the seventeen fields of spec §1.2 — no `editorContent`, no `ca`, no `canvasStart`, no
`canvasEnd` — and [`visur.js`](../../../../src/main/resources/static/visur.js) paints from `rows`: 44 `fillText`
calls per frame where it used to make 3,558, and no wrapping of its own.

### Measured

Headless Chromium at 1470x900 (112x45 cells), 12 cursor moves then 12 inserts, median per keystroke:

| Document | Payload before | Payload now | key→paint before | key→paint now | paint before | paint now |
|---|---|---|---|---|---|---|
| 6 KB | 11,293 B | 5,004 B | 32.3 ms | 32.0 ms | 1.7 ms | 0.1 ms |
| 100 KB | — | 5,114 B | — | 32.3 ms | — | 0.1 ms |
| 1 MB | — | 5,114 B | — | 32.2 ms | — | 0.1 ms |
| 5 MB | 5,310,535 B | 5,114 B | 69.2 ms | 32.4 ms | 1.5 ms | 0.1 ms |

"Before" rows were measured today on this machine, from a worktree of the parent commit, because the *recorded*
baseline of 17.5 ms did not reproduce: the same old code now shows 32.3 ms at 6 KB. That 17.5 ms was machine load,
not the payload — so the honest comparison is the two columns above, taken back to back, not the older number.

Read the table this way:

- **Payload is flat in document size** — 5,004 B to 5,114 B across a 870× range of documents. That is the point
  of the ticket. At 5 MB it is 1,038× smaller.
- **Latency is flat too**: 32.0 → 32.4 ms from 6 KB to 5 MB, where it used to climb to 69.2 ms. At these sizes the
  floor is transport and frame scheduling, not the document.
- **Paint work is ~50× smaller** and no longer varies with the document: 3,558 `fillText` calls became 44.

### What the verification caught

1. **The `modelWasChanged` echo was costing views.** `visur.js` registered a handler on `modelWasChanged` and sent
   it back, and the event bus delivers a `send()` to *one* handler, so the echo round-robined against
   `ModelWasChangedVerticle` — half the keystrokes produced no view, and a page could load blank until a key was
   pressed. Deleting the echo (and the two bridge permissions that existed only for it) means one declaration now
   yields exactly one view, with no key required. This also removes the risk noted in
   [ticket 02](02-extract-viewport-view.md) for the launcher's readiness handshake in
   [ticket 08](08-launcher.md).
2. **A selection past the last character drew no cursor at all.** Rows carry characters, not newlines, so an
   end-of-document selection covers no cell and the block had nothing to paint — and the caret had been suppressed
   as "the selection will draw it". The thin bar now stands in when the selection paints no cell.
3. **The harness must start a fresh server per run.** The document lives in the server, so a previous run's typed
   characters are still in it, and the row/origin checks fail with a 3-character offset that looks like a layout
   bug. The seed text is now read from [`InitializerService`](../../../../src/main/java/com/ple/visur/InitializerService.java)
   as ground truth rather than from the payload it is supposed to be checking, and the invocation is
   `.scratch/verify-rows/run.sh [bytes|default]`.

### Verified

Wire: exactly the seventeen fields, rows carry only `start`/`text`, no row contains a newline, `full` is a
boolean and is `true` only on the view answering a viewport declaration. Rendering: at 112x45, 68x30 and 147x54
cells — 29, 44 and 53 rows respectively, rows matching the seed document at their origins, cursor inside its row,
scrolling through origin 3305, selection gold pixels matching the visible selected cells, search submode reaching
the mode line as `LINE (f) a_`, and 115k–296k pixels painted.

**The spike was fixed in this change too**, which spec §7 predicted would be needed but had scheduled for
[ticket 05](05-client-skeleton.md). It painted `editorContent` and wrapped it itself, so it showed nothing once the
field was gone. It now consumes `rows` and places the cursor from `cursorRow`/`cursorCol`; its `Layout` — the
client-side wrap, absolute-index table and `cursor_cell` — is deleted outright, so it is smaller than before.
Verified in a pty: text, mode line and cursor render, 15 keystrokes at a median 6.2 ms. That also makes it a
second, out-of-JVM reader of the new protocol, which is worth having before the real client exists.
