# Remove `gg` / `G` / `DocumentJumpOp`

Type: task
Status: closed
Closed: 2026-11-03
Blocked by: —

## Work

`gg` and `G` were added without knowing `[`/`]` existed. `[ r` and `] r` reach the same places through the document
quantum, so the special-cased two-key sequence and its operator go away.

Delete:

- the `pendingDocumentStart` state machine and the `g`/`G` branches in
  [`KeyWasPressedVerticle`](../../../../src/main/java/com/ple/visur/KeyWasPressedVerticle.java) — which also removes the
  only hand-rolled multi-key sequence in the server, a nice side effect for the keymap's consistency;
- `DocumentJumpOp` and `DocumentJumpOpTest`.

**Accepted loss, recorded so nobody "fixes" it back:** `G` landed on the *start of the last non-empty line*;
`] r` lands on the *last character of the document*. On a final line reading `conclusion` those are different
characters. The design session chose the latter deliberately — "end of document" meaning "the end" is what the
quantum model implies — and `DocumentJumpOp.bottomLineStart` exists only to produce the old answer.

## Done when

- `gg` and `G` do nothing at all (no partial match, no stray `g` side effect), verified in both renderers.
- `[ r` reaches index 0 and `] r` reaches the last character, at span 0 **and** at span > 0.
- `grep -rn "DocumentJump\|pendingDocumentStart" src terminal` is empty.
- The terminal needs no change — confirmed: it has no `g` handling of its own, and forwards it as an ordinary
  character.

## Amendment this ticket carries

- [docs/reference/keys.md](../../../reference/keys.md) — drop the `gg` and `G` rows and the In-flight bullet.
## Result

Deleted: `DocumentJumpOp`, `DocumentJumpOpTest`, the `pendingDocumentStart` field, the `documentJumpConsumed` method and
the branch in `handle()` that let it swallow a keypress, and its reset in `handlePaste`. `grep -rn
"DocumentJump\|pendingDocumentStart" src terminal` is empty. The server now has no hand-written two-key sequence at
all — `ZZ` reaches the server as one key token and is an ordinary keymap binding.

`g` and `G` go to the keymap, find nothing, and do nothing. Pinned rather than assumed, in a new
[`DocumentQuantumJumpTest`](../../../../src/test/java/com/ple/visur/DocumentQuantumJumpTest.java): two presses of `g`
then `G` leave the cursor and the span where they were.

### The span-0 question this ticket inherited

[Ticket 01](01-bound-seam-and-tests.md) found that the document quantum parks the cursor at index 0 when nothing is
selected, and recorded that `] r` therefore landed on the *start* of the document at span 0 — one of the things this
ticket had to settle.

It needed no settling. Writing the acceptance tests first showed all four cases already passing, from the character
quantum and from the document quantum: at span 0 the document quantum's range collapses at the cursor when the cursor is
already at the end of the content, which is exactly where the jump leaves it, so the legacy `putCA(0)` is never reached
on that path. `] r` at span 0 lands one past the last character, where the character quantum allows an unselected caret
to sit; at span > 0 it lands on the last character. Both pinned, so the parking branch cannot come back to life quietly.

Seven tests, all through keypresses. The content has no trailing line break and ends in the word `conclusion`, so
`theEndOfTheDocumentIsItsLastCharacter` is simultaneously the new contract and the accepted loss: `G` would have landed
on the `c`, nine characters earlier.

### Found, not acted on

`isAtQuantumStart` is only ever written `true` — by `InitializerService`, by `TextInsertion`, and by the deleted
`DocumentJumpOp` — so `View.cursorIndex`'s span-0 branch, which draws the caret at the *end* of the quantum when the
cursor is not at a quantum start, is unreachable. It is one boolean, and either the writes are missing or the branch is.
