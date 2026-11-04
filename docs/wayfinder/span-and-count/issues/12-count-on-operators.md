# Count × span on `d` / `y` / `p`; `d` yanks

Type: task
Status: open
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
- Manual (user-driven) check: delete across a line boundary with a `line` fence, then `p`, and confirm the fence did
  **not** stop the delete and the paste round-trips the text.

## Notes

This ticket makes the undo gap louder: nine units of text can now vanish in one keystroke with nothing to bring them
back. That gap is pre-existing and named in the map; it is not this ticket's job to invent undo.
