# Undo and redo: snapshot ring, `u`, `R`

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

**Redo discipline.** `R` pops the redo stack. Any new content-changing command **clears** redo. `u` at the bottom
and `R` at the top are no-ops, not errors.

**`R` is the redo key.** No client work, no new wire token: uppercase letters already arrive intact, which is why
`F` and `N` are bound today. This was `Ctrl-r` when the ticket was written, and the reason it changed is worth
recording:

- the **browser** sends `event.key`, so `Ctrl-r` arrives as `"r"` *and* reloads the page — redo would destroy the
  document it was undoing unless every client remembered `preventDefault`, and the browser client has no test harness
  to prove it did;
- the **terminal** drops every CONTROL/ALT/SUPER/HYPER chord on purpose, so `Ctrl-d` cannot fire `d`. Granting `Ctrl-r`
  an exception means the safety rule has a carve-out, and the reason the rule exists is that a carve-out is easy to
  widen by accident.

One uppercase letter buys both of those back. `R` is replace mode in vim, and a vim user will reach for it wanting one
— the same class of collision as `o`, accepted for the same reason (keymaps are the next map's subject; this key is
wanted now). `u` needs nothing new either: it is an ordinary navigate binding.

**`loadFile` clears the ring.** Snapshotting a document and then loading another would leave `u` pasting one file's
text into another's. Stated here so the safe default is a decision and not an accident.

## Done when

- [x] `x d`, `u` restores the content **and** the span and cursor that were selected before the delete, so re-running the
  command is one keypress.
- [x] An insert session (`g`, type several characters, Escape) is **one** `u`, and `u` removes the words *and* the line that
  `g` opened.
- [x] `3 d`, `u` restores all of what the count removed, in one step.
- [x] `u` with nothing to undo does nothing and does not corrupt the stacks; `R` likewise at the top.
- [x] `u`, then a new edit, then `R` — redo does nothing, because the new edit cleared it.
- [x] The register is **not** restored: undo a paste and the register still holds the displaced text.
- [x] `loadFile` empties both stacks.
- [x] `R` reaches the server through both clients as it already does for `F` and `N` — a keypress test, not a client change.
- [x] The terminal's "drop every chord" rule is **unchanged**: `Ctrl-d` is still dropped, and a test keeps it that way, so
  no future reader mistakes `R` for the start of a chord exception.
- [x] `keys.md` gains `u` and `R`, and notes that undo never restores the register.
- [ ] Manual (user-driven) check: delete a paragraph, `u` three times, `R` three times, and confirm the document and
  the selection both walk back and forward without divergence. In the browser specifically: `R` does not reload the
  page, and `Ctrl-r` still does.

## Result

`UndoHistory` in the model (a `Shareable` like `ExecutionDataStack`), one hook in `KeyWasPressedVerticle.press`, two
bindings. `UndoRedoTest` holds nine cases at the keypress seam plus a load-through-`LoadFileOp` one in `UndoRedoTest`.

**The hook is the design.** `press` brackets the one `ces.execute` call with `beforeCommand` / `afterCommand`: take the
state, run the command, and if content differs push what was taken. No operator knows undo exists, so no operator has a
boundary to get wrong. The state is held rather than pushed while a mode other than navigate is open, which is the whole
of "the unit is a command and the boundary is entering navigate mode": `g`, the typing, the repeat and the Escape that
closes the session commit once, and a movement command changes nothing so it pushes nothing — `u` after `5 l` walks back
to the last edit, cursor and all, which is the consequence the Work section asked to pin.

**`undo` and `redo` clear the pending snapshot themselves** instead of committing it. Without that the state `u` arrives
at would land on the undo stack and undo would walk in place. Pushing what it leaves onto the *redo* stack is also why
redo needs no special case in `afterCommand`: the only commands that clear redo are edits.

**`isAtQuantumStart` is deliberately not in the snapshot.** It is part of where the caret is drawn when the span is 0,
so the ticket's five fields looked like an oversight and there is a test that would have caught it — but nothing in the
server sets it false, so the state it would restore never occurs. If a future key does set it, it joins the tuple.

**The register stays out**, as decided, and the test is the argument: `y` `j` `p` `u` leaves the document back and the
register holding the text the paste displaced. cx/cy are not snapshotted either — the renderer derives the caret from
`ca` (`View.cursorIndex`), and the two fields are already stale after every jump and paste, which undo neither causes
nor inherits.

**No client change, and the chord rule is untouched.** `R` arrives as an ordinary uppercase character: the terminal's
`drops_every_modifier_chord` already asserts `Ctrl-d` is dropped, and `maps_any_single_character_verbatim` now asserts
`R` arrives next to `F`, with a note that redo rides that rule rather than an exception to it. The browser page sends
`event.key` verbatim and never calls `preventDefault`, so `R` reaches the keymap and `Ctrl-r` still reloads.

## Notes

- **`u` is the answer to the `d p` trap** the paste ticket accepts: pressing `p` with a stale selection destroys text
  nobody highlighted. That is tolerable exactly because it is one `u` away — which is why undo is in this map and not
  in the fog.
- The renderer needs nothing: snapshots restore model fields, and the existing view projection picks them up.
- Ring size is a guess at 200. If it needs tuning it is one constant, and putting it in a config file is a decision for
  whoever needs more than 200.
