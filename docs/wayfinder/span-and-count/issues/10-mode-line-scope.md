# Mode line always shows scope next to span

Type: task
Status: closed
Closed: 2026-11-04
Blocked by: —

## Work

The user must be able to see, at every moment, both how big the selection is and how big the fence it lives in is —
otherwise fence promotion ([ticket 11](11-fence-auto-promotion.md)) and operator ceilings happen invisibly.

What the two renderers print today:

| renderer | non-search line | during search |
|---|---|---|
| terminal ([`mode_line`](../../../../terminal/src/screen.rs)) | ` NAVIGATE MODE  WORD QUANTUM  SPAN=1 ` | ` NAVIGATE MODE  LINE(f) target ` |
| canvas ([`visur.js`](../../../../src/main/resources/static/visur.js)) | `NAVIGATE MODE; SPAN = 1` | ` LINE (f) target _` |

Scope appears **only** in the search form. Make the non-search tail carry both, e.g.:

- terminal: ` NAVIGATE MODE  WORD QUANTUM  SCOPE=LINE  SPAN=3 `
- canvas: `NAVIGATE MODE; SCOPE = LINE; SPAN = 3`

Leave the search-submode form's shape alone — it already names the fence — but make sure the two agree on the fence's
name and that command state still wins the row when it is active.

Two things this line must also carry, both already decided elsewhere and both cheap once the tail is data-driven
rather than string-concatenated:

- the **pending count** while one is being typed ([ticket 05](05-count-prefix.md)) — a typed digit with no feedback is
  indistinguishable from a dropped key;
- the span value above 1, which flows through untouched because `span` is already an int on the wire.

Clipping matters at narrow viewports: the terminal pads to `cols` and truncates, so pick the tail order deliberately —
losing `SPAN=` on an 80-column window would undo the point of the ticket.

## Amendment this ticket carries

- [conformance.md](../../terminal-build/conformance.md) §5.9 row reads "Rows, cursor, mode line with
  mode/submode/quantum/span" — add scope, and note that the pending count is displayed when nonzero.

## Done when

- Both renderers show scope and span in every mode except command state, at 80×24 and 132×43.
- A test in `terminal/src/screen.rs` pins the exact line for a known view (the existing mode-line tests are the
  pattern), including the truncated-at-40-columns case.
- Span 3 and scope `DOCUMENT` are readable on screen while selecting three words across two lines.
## Result

Scope and span are on the row together, and so is a count while one is being typed.

```text
 NAVIGATE MODE  WORD QUANTUM  SCOPE=DOCUMENT  SPAN=3  COUNT=12
 SEARCHFORWARD SUBMODE  LINE(f) necess  SPAN=2
```

The search form keeps its shape — the fence was already there, named and parenthesised by direction — and gains only
the span. Command state still wins the row.

The count had to go on the wire for this, which is the three-places-at-once rule in `CODING_STANDARDS.md` doing its
job: `View.pendingCount`, `visur.js`, `protocol.rs` and its consumer `screen.rs`. It is an **int with 0 for none**, not
a nullable: a pending `0` means "none" to every command that reads a count (`0 l` does not move, `0 s` selects
nothing), so showing nothing for it is consistent rather than lossy. A tolerant parser means an old client against a
new server simply does not show it.

### Order is the design here

Quantum, then fence, then span, then count — left to right in the order the row loses them, because the row clips at
the right and the point of the ticket is undone if the span is what disappears. The cost is honest and now pinned:
**below fifty columns the span falls off the end**. `a_narrow_window_clips_the_right_end_of_the_tail` states the
forty-column line exactly rather than pretending it is handled. Dropping fields by priority instead of clipping them
mid-word is the upgrade if anyone works at that width.

### Tests

- `screen.rs`: the whole line asserted as a string at 80 columns (fits, and that is the requirement), the count shown
  after the span and absent when none was typed, the forty-column clip, and the search row.
- `protocol.rs`: `pendingCount` decoded, and 0 when the field is missing.
- New [`ViewPayloadTest`](../../../../src/test/java/com/ple/visur/ViewPayloadTest.java): the fence, the span and the
  unspent count reach the **serialised** payload. A field that exists in Java but not on the wire is a mode line that
  silently reads zero, and that is the failure this guards.
- `paint.rs`: one expected cursor column moved by eight, which is exactly the width of ` SCOPE= ` — the mode line got
  longer, and the compositor noticed.

Verified against a real server in a real pty at 100 columns: typing `3` put `COUNT=3` on the row, `s` turned it into
`SPAN=3` with `SCOPE=LINE` beside it and no leftover count, and `[ r` / `] r` reached both ends. The canvas is not
verified here — it has no test harness, so it needs a look in the browser.

### Found on the way

The live pty test `live_multiline_paste_and_document_jumps` was still driving `g g`, `G` and `s`+digit: the grammar
[ticket 05](05-count-prefix.md) and [ticket 09](09-remove-gg-and-capg.md) deleted. Nothing failed because those three
tests are gated behind `VISUR_E2E` and a server with a document, and **they fail at their first assert against a
freshly started server on `HEAD` too** (baseline run 2026-11-04: a fresh buffer is empty, and the test asks for more
than 500 painted characters before it types anything). The harness cannot seed the document it requires, which is how
a test can disagree with the editor for two tickets without anyone hearing. Its presses now use the real grammar;
running it is still a manual precondition.
