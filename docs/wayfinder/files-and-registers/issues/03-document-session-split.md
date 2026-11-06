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

## Result (the classification, written before the code lands)

Two owners is the ticket's vocabulary; the inventory says the model has **four** kinds of thing in it, and naming the
other two is what makes the table complete. Everything below is a fact about the code as it stands, taken by scanning
every `.get(key)` / `.put(key, …)` of the shared map and every `getGlobalVar("…")` / `putGlobalVar("…")` of the string
map behind it.

**`Document`** — the text, and what it costs to know it. One per process until the screens map, then `Map<Path, Document>`.

| state | why here |
|---|---|
| `editorContent` | the text |
| `sourceFilePath` | the name the text came from; [06](06-swap-files.md) names the swap file after it |
| `undoHistory` | undo is a property of the text: two pairs of hands undo the same edits, which is the ticket's own proof |
| saved baseline | [04](04-dirty-tracking.md); dirty is content ≠ baseline, and both sides of that comparison are the document's |
| swap identity | [06](06-swap-files.md); decided here so 06 has a home to write into |
| `nl` (global var), `ca`→`cx`/`cy` memo (`cursorPositionDCHolder`) | **derived caches of content.** They move with the document and are invalidated by content, not by a resize. Checked: the data-class layer never reads `canvasWidth` — `grep canvasWidth src/main/java/CursorPositionDC/` is empty — so `cx`/`cy` are logical columns, not display columns, and the memo is genuinely document-only. It is *not* a live two-client bug, and this table would have been wrong to claim it was. |

**`Session`** — a pair of hands. One per open connection, held by the thing ticket
[02](02-viewport-per-client.md) already made per connection: `Connections` grows from `id → viewport` to `id → session`,
so there is one registry of per-client state and not two.

| state | why here |
|---|---|
| `quantumStart`, `quantumEnd`, `ca`, `virtualCX`, `isAtQuantumStart` | where these hands are, and the column they want back when they move vertically |
| `span` (global var) | a selection belongs to the window that dragged it |
| `cursorQuantum`, `scopeQuantum` (global vars) | which quantum *this* window is moving in |
| `editorSubmodeStack` | `[` in one window is not `[` in another |
| `pendingCount`, `executionData` | a count lives one keypress, in one pair of hands; a command runs for one client |
| `commandStateContent`, `commandCursor`, `isInCommandState` | the command line is a window, not a property of the text |
| `previousSearchTarget` (global var), `previousSearchDirectionWasForward` | the last thing these hands asked for |
| `editorMode` | one client inserting while another is normal is the reason sessions exist at all |
| viewport (cols/rows), `canvasStart`, `fy` | where this window is looking — ticket 02's argument, extended: `fy`/`canvasStart` are *derived from* the viewport, so they cannot outlive it |
| `tempCount`, `tempInsertSeparator`, `tempTypedText` (`SessionText`) | the insert being repeated happened to one pair of hands |
| `includeSeparators`, `lineWrapping` | decided against the third bucket: they are **mutable**, so they are not configuration, and both change what a window *shows* — vim makes `wrap` window-local for the same reason. Effective values live on the session, seeded from configuration defaults. Asserted by a test: toggle separators in one session, the other still draws its own. |
| `message` | ticket [01](01-message-surface.md)'s lifetime is "the last time the user asked"; a second window never asked, so a failed save in one must not appear in the other's mode line |

**`Configuration`** — built at boot, read at runtime, never written by a command. Not model state, so it leaves the map
with the rest: `modeToKeymap` (which holds the `KeymapMap` — the enum name and the accessor name disagree, and the split
fixes the naming at the same time), `keyToQuantumName`, `quantumNameToQuantum` (the ticket already called this one out:
built once, never written), and the defaults the session values above start from.

**`Registers`** — `yankRegister` (global var), the selected register. Neither document nor session: the map decided
registers are **global** because there is one terminal emulator, so it gets its own module now, and
[09](09-register-model.md) fills it. Putting it on the session would be the map's decision reversed by a refactor.

**`connection`** — stays exactly where ticket 02 put it: the coupler's adopted "for whom", which is what resolves
`session()`. It is neither owner's state; it is the argument neither method takes.

**Dead: 10 of 35 enum values are never used as a map key** — `contentY`, `keyBuffer`, `keymapMap`, `operator`,
`operatorToService`, `quantum`, `newlineIndices`, `span`, `previousSearchTarget`, `keyPressed`. (The last was found by
being *not* dead and then being a false positive: three classes do `keymap.get(keyPressed)` on a local `Keymap`.) Four
of them are traps with real names on them: `span`, `quantum`, `newlineIndices` and `previousSearchTarget` all name state
that exists — under a global-var string, or inside `nl`, or in the case of `previousSearchTarget` written but never read
back. Deleting them is part of the ticket, because "the enum goes away or is split" cannot mean "plus nine values that
nothing touches".

**Meanwhile, the enum was never the model.** `ca` — the cursor's content address — `cx`, `cy`, `span`, `nl`,
`cursorQuantum`, `scopeQuantum`, `yankRegister`, `previousSearchTarget`, `includeSeparators`, `lineWrapping`'s cousin and
the three `SessionText` temps live in one untyped `Map<String, VisurVar>` reached by string. A third of the state,
including the cursor itself and the thing [09](09-register-model.md) is about to build on, bypasses the enum entirely.
Any split that inventories only `EditorModelKey` leaves the cursor in a string map and calls the model owned.

## Seam

`ServiceHolder.editorModelCoupler` stays — it is the 60-read-site locator, and moving it is a different ticket. What
splits is what it answers: `document()` and `session()`, where `session()` is the session of the connection adopted at
dispatch. Operators stop seeing one `getX`; they see `emc.session().getSpan()` or `emc.document().getContent()`, and the
compiler finds the 57 answers for us.

That is the ticket's first shape (a context at the point of the call), with the threading done by the locator instead of
by `Operator.execute`'s signature: `session()` cannot be called without a connection having been adopted, and the value
it returns is an object a test can hold, compare and pass around — not a `Session.current()`. It fails like the second
shape only if `session()` resolves to one shared object, which is precisely what the wire test below makes impossible.

**Sizing, from the same scan:** 77 accessors; 452 accessor call sites across 62 classes in main; 564 across the tests.
The test number is the one to watch — a facade kept alive for the tests would be the half-split this ticket forbids, so
the tests are edited too, mechanically, accessor by accessor, against the table above.

## Test seams (to confirm before any test is written)

1. **The wire, two connections, one document** — the proving test, in the harness `ViewportPerClientTest` already built.
   Client A and client B at different widths: different cursor, different span, different scroll, **same content**; an
   edit in A is visible in B; `u` in B undoes A's edit, because the ring is the document's; `includeSeparators` toggled
   in A changes B's drawing not at all. If this passes, the split is real, because every one of those assertions fails
   when `session()` returns a shared object.
2. **The objects themselves** — one `Document`, two `Session`s, no verticles: cursor, span, submode stack, pending count
   and command state are independent; content, undo ring and path are shared. Small, fast, and it names the model for
   whoever reads the classes first.
3. **The existing 217 Java and 59 Rust tests, unchanged in assertion** — the ticket ships no behaviour, so this is the
   acceptance the ticket asks for. Expect the call sites to move and the expectations not to.


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
