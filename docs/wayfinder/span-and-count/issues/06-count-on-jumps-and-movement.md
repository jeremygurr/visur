# Count reaches `[` `]` and movement

Type: task
Status: closed
Closed: 2026-11-03
Blocked by: 05, 07

## Work

Two consumers of the count from [ticket 05](05-count-prefix.md), one decision each.

**`[` and `]` move exactly one quantum per press.** Today `quantumEnd` lands on `scopeBounds[1] - 1` computed *with
the live span*, so `] w` at span 3 jumps three words. Change it so one press moves one unit and `3 ]` moves three,
which makes the jump predictable independently of how much text is selected. The span still describes the window that
travels with the cursor — moving the cursor does not change the span, and the span does not change where a count says
to go.

**Movement scales both axes.** [`RelativeMoveOp`](../../../../src/main/java/com/ple/visur/RelativeMoveOp.java) pops
`dy`/`dx` and hands them to the cursor quantum as a `MovementVector`; multiply both by the count before that call so
every quantum inherits it uniformly, rather than teaching each `move` about counts:

| keypress | cursor quantum = character | cursor quantum = word |
|---|---|---|
| `3 l` | three characters right | three words right |
| `2 j` | two lines down | two lines down, same column |
| `3 d`-style counts | not here — [ticket 12](12-count-on-operators.md) | — |

A count of `0` on movement is a **no-op** — arithmetic, not a special case. With a span, the whole window slides;
the selection never grows because a count was typed.

## Done when

- Tests: `3 l` and `2 j` from a known position with character and with word quanta; `0 l` does not move; `[`/`]` land
  one unit away at span 1 and at span 5; `3 ]` lands three units away.
- A movement with a pending count clears it (ticket 05's rule, asserted here rather than assumed).
- Arrow keys behave identically to `hjkl` at the same count.

## Result

Thirteen tests in [`MovementCountTest`](../../../../src/test/java/com/ple/visur/MovementCountTest.java), all of them
keypresses through [`KeyWasPressedVerticle.press`](../../../../src/main/java/com/ple/visur/KeyWasPressedVerticle.java)
rather than direct operator calls, because two of the ticket's asks — the count is spent by the key it belonged to, and
arrows match the letters — only exist on that path. Ten fail before the change. The three that do not are pins: the
two span-1 baselines the change must not disturb, and the `0 ]` decision below.

`3 l` moves three quanta in both axes and every quantum, `2 j` two lines keeping the column, `0 l` does not move,
`3 ArrowRight` equals `3 l`. `]` walks count quanta and the span only decides the width of what lands there, so `] q`
from the line quantum ends at the same character at span 1 and span 5 — one selects one character there, the other
five.

### The count belongs to a key sequence, not a keypress

`3 ]` retires the count the moment `]` lands, so `w` never sees it: ticket 05's rule is "the next keypress ends the
count, whether or not it matched anything", and `]` matched. So the rule gains the boundary the ticket's own example
needs — a count survives a keypress that leaves a submode open, since that is what "the command is not finished"
means, and retires on the first keypress that returns you to navigate mode ([`CountPrefix.retireUnlessDigit`](../../../../src/main/java/com/ple/visur/CountPrefix.java)).
Digits are still refused inside a submode ([`CountPrefix.record`](../../../../src/main/java/com/ple/visur/CountPrefix.java)
only counts in navigate mode), so `] 3 w` is not a way to put a count in the middle of a command.

### Multiplying the vector would have done nothing

The ticket's sketch was `dx * count` before handing the vector to the quantum.
[`CharacterQuantum.move`](../../../../src/main/java/com/ple/visur/CharacterQuantum.java) reads only the vector's
*sign*, so a scaled vector moves one character: the count instead loops the call in
[`RelativeMoveOp`](../../../../src/main/java/com/ple/visur/RelativeMoveOp.java). That keeps the quantum ignorant of
counts, which was the ticket's stated reason, and it needs a fresh `MovementVector` per unit because
`RegexQuantum.move` and `LineQuantum.move` spend `dx`/`dy` as they walk — the same vector twice over is one movement.

### Two things the ticket did not ask for and this did not do

`0 ]` is `]`. A movement can be a no-op, but a jump's own arithmetic cannot express zero quanta: bounds at span 0
collapse and `quantumEnd` then lands one *before* the frame's start, so the count is floored at one for the jump and
pinned as a decision rather than left to chance.

`3 [` is still one quantum back. A quantum's start edge is the same index whatever the span — `bounds[0]` ignores it —
so walking back N of them needs arithmetic the bound functions do not have. `quantumEnd` reads the count and
`quantumStart` says why it does not.
