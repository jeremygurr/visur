# Span > 1 in each quantum's bounds

Type: task
Status: open
Blocked by: 01

> [!note] Most of this landed with [ticket 01](01-bound-seam-and-tests.md)
> The seam tests specified span rather than pinning the old clamp, so character, line and document already implement
> the rules below, with tests. What is left is the **word** quantum: its tests pinned two behaviors that look wrong
> rather than confirming what is right, and they are named at the bottom.

## Work

Make the static bound functions from [ticket 01](01-bound-seam-and-tests.md) mean what their `span` parameter has
always claimed, per quantum:

- **character** — `[ca, ca + span]`, clamped only at the end of the content. It deliberately crosses line breaks:
  wrapping is a display concern, and the existing code already treats the last newline index as the content end
  rather than a wall. Today it returns `{ca, ca + 1}` and never looks at `span`.
- **word** (`RegexQuantum`) — already loops `span` times in `getBoundaries`, and its `move` already spends a span per
  quantum crossed. Verify with tests rather than trusting it: what must hold is that span 2 covers *both* words and
  the whitespace between them as one range, and that two words at the end of a line behave the same as two words in
  the middle of one. Fix only what the tests show is wrong.
- **line** — `bounds[1]` is the end of the `(cy + span - 1)`th line, not of `cy`. Note the existing end-of-content
  special case (the last index may be a phantom line) and keep it working at span > 1.
- **document** — whole document at any span ≥ 1. It does **not** clamp to 1, and it does not error above 1: with one
  document, "this document and the next two" is the same selection as "this document". The number stays legal because
  documents are expected to multiply.

## Done when

- New tests, one per quantum, asserting the exact range at span 1, 2, 3 from a known cursor position — including the
  case where the requested span runs off the end of the content.
- Span 0 still means "no selection": every quantum returns `{ca, ca}`.
- The word-quantum tests are written from the spec above, not from what the current loop happens to do, and any place
  those disagree is reported here before it is changed.

## Notes

The selection is `[start, end)` — inclusive start, exclusive end — which is what both renderers already assume
([`is_selected`](../../../../terminal/src/screen.rs), [`drawSpan`](../../../../src/main/resources/static/visur.js)). Keep
it; do not invent an inclusive end.

## Remaining

Both pinned in [`WordQuantumBoundsTest`](../../../../src/test/java/com/ple/visur/WordQuantumBoundsTest.java), both
caused by the walk to the next bound not stopping when the cursor is not on a match:

- **A cursor on whitespace selects the word before it.** `"one two"` at index 3 with span 1 gives `{0, 3}` — the
  selection runs back through `"one"` because `quantumStart` walks backwards while characters match, and a space does
  not stop it going into the word to its left. Arguably `\S+` has no quantum under a space and the answer is
  "nothing selected".
- **In whitespace-only content, a span above 1 selects the whitespace.** `" "` at index 0 with span 3 gives `{0, 1}`:
  the loop fails to find a quantum end, then `getNextBound` advances to the end of the content and that gets reported
  as the selection.

Settling either means deciding what the word quantum *is* when the cursor sits between words, which is a question for
the cursor-quantum model rather than for this ticket's arithmetic — worth checking against how `[` and `]` already
behave from the same position before changing what `s` selects.
