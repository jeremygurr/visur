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

- A nameless document with content **has a `.sw*` file** within the debounce window, and it is asserted at the path the
  rule names — cwd for the terminal, `~/.local/state/visur/browser/` for the browser. The two paths are different tests.
- Two nameless documents get two different slots. With `.swa`…`.swz` occupied, the next is `.swa1`, asserted rather than
  reasoned about.
- Nothing ever overwrites a pool slot, asserted by occupying one and opening a third document.
- `:w ideas.txt` deletes the slot. `:q!` deletes it. `ZZ` deletes it. A failed save does **not**.
- Opening a named file whose `.<name>.sw*` exists offers exactly three answers and does all three correctly: **recover**
  loads the swap and marks the document dirty; **delete** removes the swap and opens the file clean; **read-only** opens
  the file and refuses to write it.
- **Nothing is recovered silently.** A test asserts that no path exists where a buffer's content changes at boot without
  the user having chosen it, which is the current behaviour's defining bug.
- The `neverSavedDocumentRecoverableFromSwap` and `crashDuringSaveRestoresPreviousFile` tests are rewritten against these
  rules rather than left asserting the old ones, and `FileIoTest` says which rule each test holds.
- The half-second hole is stated out loud: content typed in the last 500 ms is not in the swap. Accepted, because a
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
- If `.visur-swap`/`.visur-recovery` and the `.sw*` pool both ship, there are three naming schemes on disk. The old two
  are deleted in this ticket — no compatibility window, and a swap file nobody can name is a swap file nobody recovers.
