# Undo and redo: snapshot ring, `u`, `Ctrl-r`

Type: task
Status: open
Blocked by: 02, 03

## Work

Nothing in the repo has undo. `CommandStateService.history` is command-line recall for `:`, not this.

**A ring of snapshots.** Before a command mutates content, push `(content, cursorQuantumStart, cursorQuantumEnd, span,
ca)` onto an undo stack, ring-buffered at ~200. Redo is a second stack. A 5 KB document copied per command is nothing
beside the render cost, and snapshots **cannot get a boundary wrong** — an inverse operation per operator is one chance
per operator to silently corrupt the document, which is the exact failure undo exists to prevent.

**The unit is a command; the boundary is entering navigate mode.** That is one choke point, and it makes the awkward
cases fall out for free: an insert session — `g`, the typing, and [05](05-insert-repeat.md)'s repeat — is one unit,
because none of it reaches navigate mode until Escape. `3 d` is one unit.

**Only content-changing commands snapshot.** `u` after `5 l` does nothing rather than appearing broken; undoable
movement is a jump list, a different feature. Consequence to pin: `u` after a movement restores the state from before
the last *edit*, so the cursor jumps back — that is vim's behavior and it is what makes undo show you what came back.

**Restore content, cursor and span. Never the register.** Accepted asymmetry, pinned by a test: undoing a paste leaves
the register holding the text that paste displaced. Someone will later "fix" this by including the register; the test is
the argument against it, since a register restored from a snapshot can name text that is no longer in the document.

**Redo discipline.** `Ctrl-r` pops the redo stack. Any new content-changing command **clears** redo. `u` at the bottom
and `Ctrl-r` at the top are no-ops, not errors.

**`Ctrl-r` on the wire.** Collides with both clients:

- **Terminal** — [`terminal/src/keys.rs`](../../../../terminal/src/keys.rs) returns `None` for any
  CONTROL/ALT/SUPER/HYPER chord, deliberately, so `Ctrl-d` cannot fire `d`. `Ctrl-r` needs an explicit exception that
  maps to a named token, and the vocabulary grows from seven names to eight (in `keys.rs` and wherever the terminal
  parses view/key names).
- **Browser** — [`visur.js`](../../../../src/main/resources/static/visur.js) sends `event.key`, so `Ctrl-r` arrives as
  `"r"` *and* reloads the page. Without `preventDefault()` redo destroys the document it was undoing.

`u` needs nothing new: it is an ordinary navigate binding.

**`loadFile` clears the ring.** Snapshotting a document and then loading another would leave `u` pasting one file's
text into another's. Stated here so the safe default is a decision and not an accident.

## Done when

- `x d`, `u` restores the content **and** the span and cursor that were selected before the delete, so re-running the
  command is one keypress.
- An insert session (`g`, type several characters, Escape) is **one** `u`, and `u` removes the words *and* the line that
  `g` opened.
- `3 d`, `u` restores all of what the count removed, in one step.
- `u` with nothing to undo does nothing and does not corrupt the stacks; `Ctrl-r` likewise at the top.
- `u`, then a new edit, then `Ctrl-r` — redo does nothing, because the new edit cleared it.
- The register is **not** restored: undo a paste and the register still holds the displaced text.
- `loadFile` empties both stacks.
- Terminal: `Ctrl-r` reaches the server as a token and the existing "drop every chord" test gains its one documented
  exception — including a test that `Ctrl-d` is *still* dropped, so the exception is provably narrow.
- Browser: `Ctrl-r` does not reload the page (manual, since there is no JS harness).
- `keys.md` gains `u` and `Ctrl-r`, and notes that undo never restores the register.
- [ ] Manual (user-driven) check: delete a paragraph, `u` three times, `Ctrl-r` three times, and confirm the document
  and the selection both walk back and forward without divergence.

## Notes

- **`u` is the answer to the `d p` trap** the paste ticket accepts: pressing `p` with a stale selection destroys text
  nobody highlighted. That is tolerable exactly because it is one `u` away — which is why undo is in this map and not
  in the fog.
- The renderer needs nothing: snapshots restore model fields, and the existing view projection picks them up.
- Ring size is a guess at 200. If it needs tuning it is one constant, and putting it in a config file is a decision for
  whoever needs more than 200.
