# Frame chosen structurally at span 1; rename the `Quantum.java` locals

Type: task
Status: open
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
