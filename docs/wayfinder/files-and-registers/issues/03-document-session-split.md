# The split: `Document` and `Session`

Type: task
Status: open
Blocked by: 02

## Work

One process holds one `EditorModelCoupler`, and 60 reads of `ServiceHolder.editorModelCoupler` across 57 classes treat it
as "the editor". It holds content and cursor quantum with equal confidence. This map then wants to add: a saved-content
baseline (per document), a path (per document), a swap identity (per document), an undo history (per document), a selected
register (global today, per window the moment screens exist), and a viewport (per client, which [02](02-viewport-per-client.md)
already had to move). Bolting those onto the singleton means the screens map deletes them later, so the split comes first.

**`Document`** — everything that survives the window closing: `editorContent`, `sourceFilePath`, the saved baseline
([04](04-dirty-tracking.md)), `undoHistory`, the swap-file identity.

**`Session`** — everything that belongs to a pair of hands: `quantum` (cursor), `span`, scope quantum,
`editorSubmodeStack`, `pendingCount`, `commandStateContent`, `commandCursor`, `isInCommandState`, `previousSearchTarget`,
`previousSearchDirectionWasForward`, `canvasWidth`/`canvasHeight`/`fy`/`canvasStart`/`virtualCX`, the selected register.

**Ambiguous, and the ticket is not done until each is decided and written down:** `globalVariableMap` (it holds
`includeSeparators`, which is a preference, and the `tempInsertSeparator`/`tempTypedText` session-text variables, which
are not — so it is two things and should be split); `keyBuffer` and `keymapMap`; `newlineIndices` and
`cursorPositionDCHolder`, which are caches of content (document-shaped, but derived, so they may want to live with the
document and be invalidated rather than duplicated); `executionData`, which is mid-command state and therefore session.

**The seam, not the data, is the hard part.** Operators do `ServiceHolder.editorModelCoupler` and then `getX`/`putX`.
Whatever replaces that has to answer "for whom" at the point of the call, and there are 57 answers. Two shapes worth
weighing explicitly, because they fail differently: a context object threaded through `Operator.execute` (honest, and it
makes the 57 call sites show up as compiler errors, which is the only cheap way to find them all) versus a
current-session lookup inside the coupler (no call-site churn, and it silently keeps working when it is wrong — the same
way a test that agrees with a broken binding keeps working).

**No behaviour changes.** This ticket ships nothing the user can see. Its acceptance is that the existing 208 tests pass
unchanged and that the two-viewport test from [02](02-viewport-per-client.md) still passes.

## Done when

- Every `EditorModelKey` value is classified `Document`, `Session`, or *derived cache*, in a table written into this
  issue as a `## Result` before the code lands. Ambiguity resolved, not shelved.
- `EditorModelCoupler` no longer exposes document state and session state through the same `getX`/`putX` surface for any
  of the fields above; `EditorModelKey` itself goes away or is split in the same way.
- Two sessions on one document: different cursor quantum, different span, different scroll position, **same content**,
  and an edit in one visible to the other through the same undo ring. This is the test that proves the split is real and
  not a renaming.
- `SessionText`'s global vars (`tempCount`, `tempInsertSeparator`, `tempTypedText`) live with the session, and the
  insert-repeat tests pass with two sessions open.
- `UndoHistory` lives with the document, and `loadingAFileEmptiesBothStacks` still passes because `:open` creates a new
  document.
- The 57 classes are counted in the commit message, and the number is 57 or an explanation of the difference is given.
- `CODING_STANDARDS.md`'s "one key owns each piece of state" paragraph is rewritten for two owners, since it currently
  asserts a single mutable model and is now wrong.

## Notes

- **Ticket [02](02-viewport-per-client.md) closed and left you the pattern.** The model now carries the id of the
  connection currently executing, adopted at the top of every inbound handler
  (`emc.putConnection(Connections.headerOf(event))`), and `Connections` holds what is per connection. Extending the split
  means moving a field's *home* from the shared model into the per-connection entry and leaving the adoption where it is;
  it does not mean threading a parameter through the operators, and it must not mean a `Session.current()` singleton
  getter, which is the two-clients-one-window bug wearing the ticket's own vocabulary.
- **Start with the three that are already per-client in behaviour:** the scroll window (`canvasStart`, `fy`) and
  `virtualCX`. They are derived from the viewport and written during a command through the same `getCanvasWidth` calls
  ticket 02 rerouted, so today a second client's cursor math reads the first client's scroll window. They are also the
  first fields where "per session" and "per document" are genuinely contested — a scroll window is where this *window* is
  looking, which is the argument for session — so settle them first and let the answer decide the rest of the list.


- **Use `codebase-design` on this ticket.** It is an interface question with a measurable answer: a `Document` whose only
  operations are content, path, baseline and undo is deep; a `Document` that also has `getSpan` because one operator
  wanted it is the same mess with a class diagram.
- `getQuantumNameToQuantum()` is neither: quantums are immutable configuration. It should stop being model state in
  passing — it is built once and never written.
- **Do not add a `DocumentStore` or a document list here.** One document, correctly boxed, is the ticket. The fog entry on
  several documents in one view is where a collection goes, and it will be a `Map<Path, Document>` or it will be nothing.
- The swap file's name comes from `sourceFilePath`, so after this ticket the swap writer reads the document, not the
  model. That is the connection to [06](06-swap-files.md); do it there, not here.
- If this ticket is going to take a week, say so in its first hour and stop. It is the largest thing in the map and the
  one most likely to be quietly abandoned half-refactored, at which point the 57 classes are worse than they were.
  A half-split — `Document` extracted, session state still in the coupler — is a **failed** outcome for this ticket,
  because the two-projects-same-state bug it exists to prevent survives.
