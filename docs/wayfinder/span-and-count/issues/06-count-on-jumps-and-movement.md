# Count reaches `[` `]` and movement

Type: task
Status: open
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
