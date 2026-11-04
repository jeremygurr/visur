# Count × span on `d` / `y` / `p`; `d` yanks

Type: task
Status: closed
Closed: 2026-11-04
Blocked by: 05

<!-- Was 05 and 11; 11 closed on 2026-11-04 with the fence's operator ceiling struck, so nothing is left to wait for. -->

## Work

The count multiplies the span for operators, and deleting keeps what it removed. The fence does not cap them — see
[ticket 11](11-fence-auto-promotion.md), which struck that job: the only limit on an operator's extent is the end of
the content, which the bound functions already enforce.

- **Extent = count × span units** of the cursor quantum. Span 3 words + `3 d` deletes **nine words**. Plain `d` at
  span 3 stays three, which is the behavior `DeleteCursorQuantumOp` already has.
- **Capped by the end of the content** and nothing else. ~~Capped by the effective fence, so with a `line` fence a
  delete stops at the line~~ — struck: that fence would have promoted itself to `document` whenever the extent was
  wider than a line, so it would never have stopped anything. A delete that was shown five words across a line break
  deletes five words across the line break.
- **Not capped by the fence**, deliberately. `SCOPE=LINE` on the mode line is a statement about `f`, not about `d`.
- **`d` yanks what it removes** into the yank register, so `p` puts it back. Implementation is `d` = yank the extent,
  then delete it; the register is overwritten unconditionally, since there are no named registers yet (recorded as
  fog in [the map](../map.md)).
- **`3 p` pastes three copies** at successive positions.
- **Span 0 means no operator.** `3 d` and `3 y` with nothing selected do nothing. This is a deliberate refusal: an
  editor that deletes text it never highlighted is an editor nobody trusts with a count. `3 s d` says it explicitly,
  with the text shown first.

Where the multiplication belongs: the count lives in the model ([ticket 05](05-count-prefix.md)) and the span already
does, so the operators should read both and the multiplication should be one named expression — not a `count` field
threaded through every operator constructor.

## Done when

- Tests: `3 d` at span 3 word deletes nine words and leaves the register holding exactly those nine; `2 d` at span 4
  line deletes eight lines (the fence is not involved, per ticket 11); `d` at span 0 and `3 d` at span 0 both no-op;
  `3 p` inserts three copies; `y` then `d` then `p` pastes the *deleted* text, proving the register was overwritten —
  the documented behavior, pinned so a later change to it is a decision rather than a surprise.
- The deleted region, the yanked string, and the span they were computed from all agree in one test per quantum.
- [ ] Manual (user-driven) check: delete across a line boundary with a `line` fence, then `p`, and confirm the fence did
  **not** stop the delete and the paste round-trips the text.

## Result

**One named expression, three operators that read it.** [`Quantum.operatorExtent()`](../../../../src/main/java/com/ple/visur/Quantum.java)
is the count times the span, in the cursor quantum, measured from `cursorQuantumStart`:

```java
int units = CountPrefix.countOrOne(emc) * emc.getSpan();
return getBoundaries(emc.getCursorQuantumStart(), emc.getNextLineIndices(), units, false);
```

Measured from the selection's start rather than the cursor, so an operator always includes what `s` showed and the
count never widens what is on screen. No `count` parameter appears in any operator constructor; nothing writes the
span, which stays the number `s` set ([the rule](../map.md)).

- [`YankSpanOp`](../../../../src/main/java/com/ple/visur/YankSpanOp.java) yanks `content.substring(extent)`.
- [`DeleteCursorQuantumOp`](../../../../src/main/java/com/ple/visur/DeleteCursorQuantumOp.java) removes that same
  range and puts exactly it in the yank register — so "what was deleted", "what was yanked" and "the range the span
  was computed from" are one value, which is what the Done-when asked for in three places.
- [`PasteYankRegisterOp`](../../../../src/main/java/com/ple/visur/PasteYankRegisterOp.java) repeats the register
  `countOrOne` times. Three copies pasted at successive positions and one insertion of three copies are the same text;
  the insertion is one call, so the cursor is re-seated once instead of twice.

`d`'s old guard was `if (span > 0)`; it is now `if (extent[1] > extent[0])`, which refuses three cases instead of one —
no selection, a count of zero, and any extent that names no text — and leaves the register holding what it held.
`YankSpanOp`'s `span == 0` early return went with it: one rule, "an empty range touches nothing", in one place per
operator rather than two.

### Zero needed no branch, which is why it has none

`0 d`, `0 y` and `0 p` do nothing, and no code decides that. `0` × span is a span of nothing, which every quantum
already answers as an empty range, and the operators refuse empty ranges. A guard for it was written and then deleted:
with it removed, no test in the suite could tell. It is pinned as behavior instead, in
`OperatorCountTest.aCountOfZeroIsAnOperatorThatDoesNotHappen`, including the part worth pinning — `0 y` does not blank
the register with the nothing it yanked.

### Tests

Eight in [`OperatorCountTest`](../../../../src/test/java/com/ple/visur/OperatorCountTest.java), all through keypresses,
because the extent is the count *and* the span together and either half read from the wrong place looks the same at
one. One per quantum (character 2×2, word 3×3, line 2×4, document 2×1) asserting the remaining content *and* the
register together, plus the empty-selection refusal, the zero count, `3 p`, and `y`-then-`d`-then-`p` reconstructing
the original document exactly — which is only possible if the register holds precisely what the delete removed.

### Found on the way

**Deleting N lines leaves one newline behind**, so eight lines deleted from the top leave an empty line where line one
was. This is not the count: it is the line quantum's end edge, which stops before the newline that ends its last line
because that newline is not *between* the selected lines — `LineQuantumBoundsTest.aLineEndingInANewlineEndsBeforeItWhenAnotherLineFollows`
pins it, and it is the same rule that stops a word span at the end of the last word and leaves the following space.
Pinned in `aCountOfTwoDeletesEightLinesWhenFourAreSelected` rather than smoothed over. **Open question for the user:**
vim's `dd` takes the line entirely, and an operator that leaves an empty line behind will read as a bug to anyone
arriving from vim even though it is consistent here. Changing it means changing the line quantum's end edge, which no
ticket in this set owns.

**The fixture could not set the span without the range.** `d` and `y` act on the stored `cursorQuantumStart/End`, not
on a range re-derived from the span, so a test that wrote `putSpan(3)` on a booted model was describing a state the
editor cannot be in — the range and the span are set together by every path that writes either. The fixture now leaves
the span to what the boot established and lets each test press `s`.
