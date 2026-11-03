# Frame chosen structurally at span 1; rename the `Quantum.java` locals

Type: task
Status: closed
Closed: 2026-11-03
Blocked by: —

## Work

Two small changes in [`Quantum.java`](../../../../src/main/java/com/ple/visur/Quantum.java), one behavioral, one not.

**Behavioral.** `getScopeAndCursorQuantums` decides which of two quanta is the frame by measuring both bound lengths
**at the live span** (`getQuantumBoundsLengthAtSpan(spanMinimumOne)`). At span 1 that ranking matches the nesting;
at span 20 it can invert — twenty words can out-measure twenty short lines — so the same keystroke would treat a
different quantum as the frame depending on selection size. Rank them **structurally, at span 1**: which quantum is
coarser is a fact about nesting, not about how much is currently highlighted. The requested span then applies to the
cursor quantum only.

**Not behavioral.** Rename the local `scopeQuantum` / `scopeAndCursorQuantums` to `frameQuantum` /
`frameAndCursorQuantums` (and update the comments), because the model already has a *different* thing called
`scopeQuantum`: the search fence set by `/`, read by [`SearchForwardOp`](../../../../src/main/java/com/ple/visur/SearchForwardOp.java)
and `SearchBackwardOp`, never consulted by `[`/`]`. Two unrelated objects, one name, one file this ticket edits.
The search fence keeps its name and its meaning.

## Done when

- `] w` and `[ w` land in the same place at span 1 and at span 20 with the cursor quantum unchanged.
- No identifier in `Quantum.java` contains `scope`.
- The rename is provably inert: `git diff --stat` shows no other file touched, and no behavior test changes result.

## Notes

The observable consequence of the naming confusion is testable today and worth confirming before starting: press
`/ q` (fence = one character) then `] r` — the jump to end of document still works, proving `[`/`]` ignore the fence.

## Result

The frame is now ranked at span 1, and the locals say `frameQuantum`. `git diff` is one file: the rename touched no
identifier outside [`Quantum.java`](../../../../src/main/java/com/ple/visur/Quantum.java), no existing test changed,
and the model's `scopeQuantum` — the search fence, read by
[`SearchForwardOp`](../../../../src/main/java/com/ple/visur/SearchForwardOp.java) — keeps its name and its meaning.
The one remaining `scope` in the file is a sentence saying the two are not the same thing.

[`QuantumFrameTest`](../../../../src/test/java/com/ple/visur/QuantumFrameTest.java) pins it: the cursor quantum is
`character`, the keystroke is `] w`, and the assertion is which quantum you are left in — the frame decision, observed
through the model rather than through the arithmetic. At span 1 it is `word` before and after the change; at span 20 it
was `character` and is now `word`. The mirror through `[ w` is pinned too, and fails against the old ranking as well.

### The inversion is not the one the ticket described

The ticket's example was twenty words out-measuring twenty short lines. That cannot happen today: when
`getNextBound` is called on a character that does not match, it returns `bound + 1` — one character, not the next word
— so a word window crossing blank lines grows one character per span, exactly like a line window crossing the same
blank lines. The word quantum can therefore never out-measure the line quantum at an equal span, and the example
becomes reachable only once [ticket 02](02-span-per-quantum.md) settles that walk.

What does invert today is `character` against `word`, in the same kind of place: a run of blank lines. At span 1 a
character measures one and a word that is not there measures nothing, so the character quantum frames, as nesting says.
At span 20 both measure twenty, the comparison ties, and the `else` branch hands the frame to the operand — so `] w`
from index 30 of a file with twenty-five blank lines jumped to 91 and left the cursor in the character quantum, where
at span 1 the same keys land on 37 in the word quantum. Ranked at span 1 it lands on 48, the end of the twenty-character
frame, and selects the requested twenty words from there.

Ranking at span 1 removes the flip for any pair whose span-1 order is strict. A pair that ties at span 1 — a blank line
against a blank line, where neither measures anything — still frames by the operand, at every span, which is at least
consistent.
