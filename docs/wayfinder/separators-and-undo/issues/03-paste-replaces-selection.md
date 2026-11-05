# `p` replaces the selection and takes what it displaced; `P` inserts before it

Type: task
Status: closed
Blocked by: 02

## Work

`p` becomes `d` followed by an insertion, and `P` becomes the insertion alone. Both insert at
`cursorQuantumStart`, in the cursor quantum the user is in, and neither is otherwise quantum-aware.

**`p`, in order:**

1. capture the text the selection covers (the extent [ticket 12](../../span-and-count/issues/12-count-on-operators.md)
   computes: count × span, capped only by content end);
2. delete it;
3. insert the register's contents at `cursorQuantumStart`;
4. **if step 1 captured anything, make it the register's contents.**

Step 4 is the rule to get right: the register holds **what this keypress removed, and only then**. Over nothing —
no selection, or an empty range such as a blank line at the word quantum — the register is untouched, so `p p` with
no selection pastes twice instead of pasting once and then nothing.

`3 p` inserts three copies and still records the displaced text **once**: the register is a place, not a count. The
count multiplies the paste only. What disappears is exactly the selection on screen — never `operatorExtent()`'s
count × span, because a key called "paste" deleting four words nobody highlighted is the failure the previous map's
ticket 12 refused for `d` with more force.

**`P`** inserts at `cursorQuantumStart` and does **not** delete. It must leave the selection covering the same text it
covered before, **shifted right by the inserted length** — not collapsed.

That last point is why `P` cannot reuse [`TextInsertion.insertAt`](../../../../src/main/java/com/ple/visur/TextInsertion.java):
it re-seats the bounds at span 0, which would erase the highlight of text it never touched. Add a second insertion
path that keeps the span; do not add a flag to the first.

**Where the caret lands**, both keys: on the last character of the pasted text, span 0 for `p`. Note this means the
round-one worry ("`d` then `p` deletes another six words", because ticket 04 keeps the span alive after a delete) does
**not** compound: `d` leaves the span, but `p` finishes with span 0.

## Done when

- `p` over a word selection replaces exactly that selection with the register and leaves the register holding what it
  replaced. Through keypresses: select, `y`, select elsewhere, `p`.
- `d` then `p` puts the deleted text back **where the cursor is now**, not where it was deleted from, and the register
  then holds whatever `p` displaced.
- `p` and `p` again with no selection pastes the same text twice — the register was not blanked by the first paste.
- `p` on a blank line at the word quantum still pastes (the empty-range case).
- `3 p` inserts three copies with a selection and records the displaced text once.
- `P` over a three-word selection inserts at the selection's start and the **same three words are still selected**,
  shifted. Pinned as a test, and asserted through the drawn span on the wire, not just the model.
- `P` with nothing selected inserts at the caret and leaves the span at 0, so `p` and `P` differ only when there is a
  selection to disturb.
- `keys.md` gains `P`, and `p`'s row states the replacement and the register swap.
- [x] Manual (user-driven) check: `y` a line, `s` another line, `p` — the second line is replaced by the first, with no
  blank line and no leftover text; `P` on the same fixture puts the register in front and the selection survives.

## Notes

- Two keypresses now write the register (`d`, `y`) and two displace text (`d`, `p`). There is still **one** register, so
  `p` clobbering is by design and named as fog in the map.
- The `d p` sequence is worth a test even though it reads oddly: it is the most likely accidental sequence in the
  editor, and after this ticket its result is "the text you deleted, back where the cursor is, and the register now
  holding the six words you displaced" — which is only safe because undo is coming in
  [06](06-undo-redo.md).

## Result

Shipped as `p` = replace-selection-then-insert, `P` = insert-at-start-and-shift. `PasteTest` holds the nine cases this
ticket named; `OperatorCountTest` lost its two paste-related `@Disabled`s.

**One decision the ticket left open, settled here.** The Done-when bullet ("`d` then `p` puts the deleted text back")
and `SetSpanOpTest.deletingLeavesAWindowThatWideBehind` could not both hold: after a delete, a window re-derived at the
span covers the text that slid into the hole, and a `p` that replaces the selection eats it. `d` now collapses the range
to the gap and keeps the span, which is what the ticket's own test encodes and what makes the map's note true: the
reason `d` then `p` does not delete another six words is that there is no stale window for the paste to widen, and the
reason a second `p` does not either is that `p` ends at span 0. The reversed test is
`SetSpanOpTest.deletingLeavesAGapAndKeepsTheSpanForTheNextMove`; the surviving
`movingAfterADeleteRederivesAWindowThatWide` is the half of the old contract that still matters, and
`CODING_STANDARDS.md` now says which operator recomputes what.

**The count on `p` multiplies the copies, never the text removed.** Step 1 of `## Work` says the capture is "the extent
ticket 12 computes: count × span" while the prose below says "never `operatorExtent()`'s count × span"; the second is
what ships, since the first is the failure the paragraph rejects. `3 p` therefore replaces one selection with three
copies and records the displaced text once.

**`p` over nothing does not even remove the selection.** A count of zero, or an empty register, makes the paste nothing,
and an operator that does nothing displaces no text: `0 p` leaves both the selection and the register alone.

**`P` respects the count** (`3 P` inserts three copies in front and shifts the selection by all of them), which the
ticket does not mention; it is the only reading consistent with `p`, and the count of zero declines the same way.

**`p` writes the span 0 it finishes at**, since a mode line still reading `SPAN=3` after a paste that highlighted
nothing is the same lie in a different place. `P` never writes it: the selection it preserves carries the span with it.
`CODING_STANDARDS.md` names the exception beside the rule.

`P` arrives through both clients already, as `F` and `N` do — no wire or renderer change, no new key token.

The manual check passed: `p` replaced a line cleanly and `P` left the selection standing.
