# Swap files: a pool for nameless documents, vim's prompt for named ones

Type: task
Status: open
Blocked by: 01, 04, 05

## Work

**Today.** `putEditorContent` triggers `writeSwapFileDebounced` (`EditorModelCoupler.java:217,227`), which writes
`<sourceFilePath>.visur-swap` at most every 500 ms, and **returns without writing anything when the document has no
path**. At boot, `InitializerService.recoverFromSwapFiles()` (`:289`) reads the swap into the buffer, writes a
`.visur-recovery` copy next to it, deletes the swap, and says nothing. So: the document most likely to be lost — new,
unsaved, no name — has no swap at all, and the recovery that does exist silently replaces a buffer the user did not ask
to have replaced.

**Nameless documents get a swap, from a pool.** `.swa`, `.swb`, … `.swz`, and when those are taken `.swa1`, `.swb1`, and
so on. Written in the **directory visur was launched from** for the terminal client, and in
`**`~/.local/state/visur/browser/`** for the browser, which has no directory of its own. A pool slot is **never
overwritten**; it stays until the user deletes it. A document keeps the slot it got, so the nameless buffer the user has
been editing for an hour is still there tomorrow morning.

**Nameless swaps are never announced and never auto-recovered.** No prompt at boot, no message, nothing at exit. The user
opens one deliberately, with `:open ~/.local/state/visur/browser/.swb`, and from then on it behaves like any other
document: they can `:w ideas.txt` and give it a name, or keep working nameless and keep the pool slot. The reason is that
these files are not conflicts, they are just buffers that have not been named yet, and a prompt for each at every boot is
how you teach a user to hit Enter without reading.

**A named file with a `.<name>.sw*` counterpart gets a real prompt: recover, delete it, or open read-only** — vim's three
choices, because the situation is vim's: someone (possibly you, possibly a crash) has unsaved work in this file and the
file on disk is not what they left. Whichever is chosen, the answer is recorded, and **the prompt blocks the open** rather
than opening the file and complaining afterwards.

**The pool slot is deleted by the first successful `:w`** — a swap for a document that has a file is a lie the next session
will believe — **and by `:q!`**, which is the user saying *throw this away*.

**Recovery replaces the silent `.visur-recovery` copy.** The old behaviour writes a second file the user has never heard
of; recover either loads the swap into the document (dirty, because the file was never reached) or leaves the disk alone
in the read-only case, and in both cases the user was told first.

## Done when

- [x] A nameless document with content **has a `.sw*` file** within the debounce window, and it is asserted at the path the
  rule names — cwd for the terminal, `~/.local/state/visur/browser/` for the browser. The two paths are different tests.
- [x] Two nameless documents get two different slots. With `.swa`…`.swz` occupied, the next is `.swa1`, asserted rather than
  reasoned about.
- [x] Nothing ever overwrites a pool slot, asserted by occupying one and opening a third document.
- [x] `:w ideas.txt` deletes the slot. `:q!` deletes it. `ZZ` deletes it. A failed save does **not**.
- [x] Opening a named file whose `.<name>.sw*` exists offers exactly three answers and does all three correctly: **recover**
  loads the swap and marks the document dirty; **delete** removes the swap and opens the file clean; **read-only** opens
  the file and refuses to write it.
- [x] **Nothing is recovered silently.** A test asserts that no path exists where a buffer's content changes at boot without
  the user having chosen it, which is the current behaviour's defining bug.
- [x] The `neverSavedDocumentRecoverableFromSwap` and `crashDuringSaveRestoresPreviousFile` tests are rewritten against these
  rules rather than left asserting the old ones, and `FileIoTest` says which rule each test holds.
- [x] The half-second hole is stated out loud: content typed in the last 500 ms is not in the swap. Accepted, because a
  swap write per keystroke is a disk write per keystroke.

## Notes

- **The three-way prompt is the first question visur ever asks**, and [01](01-message-surface.md)'s message slot is not
  it: a message has no answers. It needs a mode of its own — text on screen, three keys, Escape does the safe thing — and
  it is worth checking that it is *three keys and Escape*, not a text prompt, because a text prompt invites a fourth
  mistake. What Escape does is a decision, and "abandon the open, change nothing" is the only safe answer.
- `recoverFromSwapFiles` runs at boot, before any client is connected. A prompt at boot is therefore a thing the *first*
  client renders, which is the first time the server has ever waited on a client. If that turns out to be expensive, the
  fallback is to open read-only and put the offer on screen as a message — the file is not lost either way, but that
  change is a decision made with the cost in hand, not in advance.
- **The pool has a concurrency hole the map knows about:** two browser sessions could pick `.swa` at the same moment. A
  create-exclusive open (`Files.createFile` and catch, then try the next slot) closes it without a lock, and it is the
  right shape anyway now that [02](02-viewport-per-client.md) has proved more than one client can be attached.
- `.swa` in the launch directory means `git status` will show it. Worth one line in `keys.md` or the README so the first
  user to see it does not file it as corruption.
- The browser directory does not exist yet and must be created on demand; the alternative, `~/.local/state/visur/` with a
  marker per client, is the same ticket's decision made differently. Note in the `## Result` which won.
- **Observed while [04](04-dirty-tracking.md) was being checked live:** a server killed while a document was dirty leaves
  `<file>.visur-swap`, the next boot recovers it into the buffer *without being asked*, writes
  `<file>.visur-recovery` beside the file, and leaves that copy there forever — a later run found `plai!n text` in the
  buffer with the file itself still holding `plain text`. The recovered document is now at least marked dirty, but the
  swap is only ever deleted by a successful save, so the resurrection happens on the *next* boot after a crash rather
  than the one during it. Whatever this ticket decides about staleness, the recovery copy needs a lifetime.
- If `.visur-swap`/`.visur-recovery` and the `.sw*` pool both ship, there are three naming schemes on disk. The old two
  are deleted in this ticket — no compatibility window, and a swap file nobody can name is a swap file nobody recovers.

## What landed

**Two kinds of swap, one owner of the spelling.** `SwapFiles` answers "where does this document's text get mirrored":
`.<name>.swp` beside a named file, or a slot from the pool for a nameless one, claimed create-exclusive off `.swa`
through `.swz` and then `.swa1`. Three places used to spell `<path>.visur-swap` by hand; all three are gone, along with
`.visur-recovery`, so there are two names on disk and both are in `keys.md`.

**The pool directory is the typing window's, and the first window to type decides it.** The ticket says cwd for the
terminal and `~/.local/state/visur/browser/` for the browser, and with one document shared by every client there is no
per-client answer available: the buffer has one swap, so the first window that types into it claims its slot in *its*
directory and the document keeps that slot while it is nameless. What a client is arrives as one word on the viewport
declaration (`client: "terminal" | "browser"`), because that message already says who the window is and there is nothing
else for the word to ride in on. Absent or unrecognised means no nameless swap — which is also what keeps every test that
edits a document without a socket out of the working tree, and is why the pool directory had to be a property
(`visur.swapDir`) before any of this could be tested at all.

**Nothing is recovered silently, and the way to get there was subtraction.** `recoverFromSwapFiles` is deleted: boot
loads the file it was given, writes nothing, deletes nothing. The three-way prompt then never had to become the thing the
Notes were braced for — a server waiting on a client that has not connected yet — because recovery is only ever a
question about *opening* a file, and `:open` happens with a window attached. So there is no boot-time prompt, and the
fallback in the Notes ("open read-only and put the offer on screen") was never needed.

**The question is a session state, not a message, but it is drawn in the message row.** It has three keys and a safe
answer, `r`/`d`/`o` and everything else, and while it is pending `press()` spends the next keypress on answering it. The
pixels are the message row because that is the only line visur has; what makes it a question is that the next key has an
answer. Any key that is not one of the three is the safe answer — abandon the open, change nothing, swallow the key —
which is Escape with the door left open, and cheaper to survive than a keyboard that half-works during a question.

**The answers**: `r` loads the swap and leaves it dirty (a swap is a record of text that never arrived), `d` deletes the
swap and opens the file clean, `o` opens the file and sets read-only, which refuses a plain `:w` and not `:w path`, since
naming a file is a louder statement than a flag. All three go through the one `LoadFileOp.install` reset, because two
copies of "make this text be this file's document" is two chances for the cursor to land in different places.

**`:q!` deletes the swap only as the last window on the document.** The ticket says `:q!` deletes it, written before the
document was shared: the swap belongs to the document and `:q!` closes a window, so `:q!` in one tab taking away the
insurance of a tab still editing is not what anyone there agreed to. Checked in both directions by mutation, since
"always" and "never" each break exactly one half of the test.

**A load that works deletes the swap of the name it left, and never the swap of the name it arrived at** — a
`.newname.swp` is somebody else's crash, and `:w newname` has no business eating it. `setSourceFilePath` with the path it
already had does nothing at all, which is a bug this ticket found on its own: `:open` of the open file was throwing away
that file's swap.

**Tests.** `SwapFilesTest` (7): the pool in order, no slot twice, an occupied slot left alone, `.swa1`, and both default
directories named. `StartupFileTest` (3): the first tests in the repo to boot the real verticle, because everything else
initialised the model by hand and that is how the silent recovery outlived everything around it. `RecoveryOfferTest` (6):
the offer, the three answers, the safe answer, and read-only giving way to a path. `FileIoTest` now states a rule per
test instead of a vim scenario: an unsaved edit survives beside its file, a failed save leaves both the old bytes and the
swap alone, a successful save takes the swap with it. `SessionsOnOneDocumentTest` (+2): two windows on a nameless buffer
is one slot, and `:q!` discards as the last window only.

**07 narrowed the boot rule the day after it landed.** "Boot pays no attention to any swap" is a true statement about a
server nobody connects to; `visur notes.txt` is a different thing, because whoever typed it is standing there. The launch
load now happens when the first window arrives, through `:open`'s door, so launching onto a crash asks the question and
`StartupFileTest.aSwapBesideTheFileIsNeitherLoadedNorDeletedAtBoot` became
`launchingOnAFileWithASwapAsksTheWindowThatArrived`. Nothing about what a swap *is* changed, and the swap still survives
an unanswered question.

**Checked live** (fat jar, scratch cwd, node client): boot ignored a swap and left it on disk; `:open` offered, `Escape`
changed nothing, `d` deleted and opened clean, `o` opened and `:w` refused with the file unchanged while `:w elsewhere.txt`
wrote; a save deleted the swap; typing into a nameless buffer in a terminal's launch directory created `.swa` holding the
typed text, and booting created nothing. The literal browser path is asserted in a unit test and not created live, since
the check would have written into the home directory of whoever ran it.

**One thing the suite was hiding.** Sessions live in a static map keyed by socket id, and the id-less session a test
uses is created on demand and never thrown away — so a question asked in one test class was pending in the next class's,
swallowing its keys, and one test had been inheriting another's cursor. `ServiceHolder.init` now closes every connection,
which is what a new model means and what `MainVerticle` already did.
