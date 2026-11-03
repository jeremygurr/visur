# Mode line always shows scope next to span

Type: task
Status: open
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
