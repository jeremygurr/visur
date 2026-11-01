# Switch the wire to viewport rows

Type: task
Status: open
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
