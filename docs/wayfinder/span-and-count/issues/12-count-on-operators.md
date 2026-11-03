# Count × span on `d` / `y` / `p`; `d` yanks

Type: task
Status: open
Blocked by: 05, 11

## Work

The count multiplies the span for operators, the fence caps them, and deleting keeps what it removed.

- **Extent = count × span units** of the cursor quantum. Span 3 words + `3 d` deletes **nine words**. Plain `d` at
  span 3 stays three, which is the behavior `DeleteCursorQuantumOp` already has.
- **Capped by the effective fence** from [ticket 11](11-fence-auto-promotion.md), and by the end of the content. With
  a `line` fence, a delete stops at the line — that is the whole point of the fence being a ceiling for operators
  rather than only for search.
- **`d` yanks what it removes** into the yank register, so `p` puts it back. Implementation is `d` = yank the extent,
  then delete it; the register is overwritten unconditionally, since there are no named registers yet (recorded as
  fog in [the map](../map.md)).
- **`3 p` pastes three copies** at successive positions, bounded by the fence.
- **Span 0 means no operator.** `3 d` and `3 y` with nothing selected do nothing. This is a deliberate refusal: an
  editor that deletes text it never highlighted is an editor nobody trusts with a count. `3 s d` says it explicitly,
  with the text shown first.

Where the multiplication belongs: the count lives in the model ([ticket 05](05-count-prefix.md)) and the span already
does, so the operators should read both and the multiplication should be one named expression — not a `count` field
threaded through every operator constructor.

## Done when

- Tests: `3 d` at span 3 word deletes nine words and leaves the register holding exactly those nine; `2 d` at span 4
  line deletes eight lines or the fence's worth, whichever is smaller; `d` at span 0 and `3 d` at span 0 both no-op;
  `3 p` inserts three copies; `y` then `d` then `p` pastes the *deleted* text, proving the register was overwritten —
  the documented behavior, pinned so a later change to it is a decision rather than a surprise.
- The deleted region, the yanked string, and the span they were computed from all agree in one test per quantum.
- Manual (user-driven) check: delete across a line boundary with a `line` fence, then `p`, and confirm the fence
  stopped the delete and the paste round-trips the text.

## Notes

This ticket makes the undo gap louder: nine units of text can now vanish in one keystroke with nothing to bring them
back. That gap is pre-existing and named in the map; it is not this ticket's job to invent undo.
