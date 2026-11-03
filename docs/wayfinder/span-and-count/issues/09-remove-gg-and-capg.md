# Remove `gg` / `G` / `DocumentJumpOp`

Type: task
Status: open
Blocked by: —

## Work

`gg` and `G` were added without knowing `[`/`]` existed. `[ r` and `] r` reach the same places through the document
quantum, so the special-cased two-key sequence and its operator go away.

Delete:

- the `pendingDocumentStart` state machine and the `g`/`G` branches in
  [`KeyWasPressedVerticle`](../../../../src/main/java/com/ple/visur/KeyWasPressedVerticle.java) — which also removes the
  only hand-rolled multi-key sequence in the server, a nice side effect for the keymap's consistency;
- [`DocumentJumpOp`](../../../../src/main/java/com/ple/visur/DocumentJumpOp.java) and
  [`DocumentJumpOpTest`](../../../../src/test/java/com/ple/visur/DocumentJumpOpTest.java).

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
