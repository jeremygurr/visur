# Dirty is a comparison, and the mode line shows it

Type: task
Status: open
Blocked by: 03

## Work

Nothing in the repo knows whether the screen matches the file: no dirty flag, no `isDirty`, nothing `modified`. Three
decisions in this map need that knowledge — the marker in the mode line, `:open` refusing to lose unsaved text, and `:q`
refusing — so it arrives here, on the `Document` [03](03-document-session-split.md) just created.

**Remember the content as saved; dirty means they differ.** The document holds the text as it was when it was last
written (or as it was when it was loaded, which is the same claim) and dirty is a comparison against it. A flag — set on
write, cleared on save — is smaller and it is wrong in a way the user will see: after `d` then `u`, the undo restores the
content exactly, so the document *is* what is on disk, and a flag would put a warning marker on a file that has no
unsaved changes. That is not a corner case, it is the most common sequence in the editor.

**The comparison runs where the view is built**, since that is where the marker is needed and where the content is already
being read. `String.hashCode()` of both sides is enough and is not worth optimising before it shows up in a profile. If
it does, the answer is a counter incremented by `putEditorContent` on the document and reset by save, not a boolean.

**`*` in the mode line, in the part that does not clip.** The line already carries mode, cursor quantum, `SCOPE=`,
`SEP=`, `SPAN=` and `COUNT=` and clips at 40 columns; the existing clip test pins what falls off first. The marker is the
one item on that line that is not "what mode am I in" — it is "what happened to my data" — so it goes where it cannot be
clipped away, and the test says which position that is.

**A brand-new document is dirty iff it has content.** An empty unnamed buffer is not unsaved work; one with three
characters in it is, and those three characters have never been anywhere except a swap file.

## Done when

- [x] Typing sets dirty; `ZZ`/`:w` clears it; the marker is asserted through the view payload, in both clients' mode lines.
- [x] **`d` then `u` clears the marker** — the document matches the file again, and the test says so explicitly, because a
  future "optimisation" to a flag will reintroduce the lie.
- [x] A document loaded from disk is clean. A document saved to a *new* path via `:w other.txt` is clean and its baseline is
  now that file.
- [x] An empty unnamed document is clean; the same document with one character typed is dirty.
- [x] Saving to an unwritable path leaves the document **dirty** and raises [01](01-message-surface.md)'s message. A
  failed save that clears the marker is the worst bug this ticket could ship.
- [x] The marker survives the 40-column clip, asserted by extending the existing clip test rather than a new one.
- [x] `:open` and `:q` read this and nothing else — no second notion of unsaved-ness appears in [05](05-command-key-and-grammar.md).

## Notes

- The baseline is content, so it costs one extra String reference per document, not a copy — content is immutable and
  replaced wholesale, which is also what makes the undo ring cheap.
- **Do not** put the baseline in `LoadFileOp` alone. `:w` has to move it too, and so does the swap-recovery path that
  loads text the user never saved: a document recovered from a swap is *dirty by definition*, since the swap exists
  precisely because it never reached the file. That last case is the test that catches a baseline written in one place
  instead of three.
- `View.render()` is the field list again (`:119-141`, decoded in `terminal/src/protocol.rs:59-93`); one more boolean,
  both clients, one commit.
- The Rust mode-line test at `terminal/src/screen.rs:216` is the one to extend; it already asserts exact strings, so it
  will fail informatively when the marker's position is wrong.
- `docs/reference/keys.md` documents what the user sees, so it gains the marker in the mode-line description. There is no
  key for dirty, and there must not be one.

## What landed

`Document` holds `savedContent` — the text as it last matched the file — and answers `isDirty()` by comparing. Three
callers move it: `putContentAsSaved` (the startup load and `loadFile`), `markSaved` (a write that succeeded), and
nothing else, which is the point: recovery reads a file too and must not clear the marker.

```java
public boolean isDirty() { return !content.equals(savedContent); }
```

`equals`, not `hashCode`, and not `==`: the last is what an "optimisation" reaches for and `DocumentTest` now defends —
its undo case hands back `new StringBuilder(...).toString()`, text that equals the file without being it, because every
edit in this editor builds a fresh `String`. Compared by reference the marker clears the first time and never again.

**`isDirty` on the wire, `*` on the mode word.** `View.render()` gained the boolean (payload now 21 fields, which
`harness/src/ws.rs` asserts — its `FIELDS` was still 17, so the contract check had been reporting `not met` since
`pendingCount` landed; it reads 21 now and `visur-harness ws` says `verdict=met`). The marker is glued to the mode word
— `NAVIGATE MODE*` — because the tail is what a narrow window clips and what an error overwrites: the clip test in
`terminal/src/screen.rs` now sets `is_dirty` and asserts the marker survives at 40 columns, and the submode label carries
it too, so insert and search show it. Command state takes the whole row for the command line, including the row you are
typing `:w` into. The browser draws it in the same place (`visur.js`), and `docs/reference/keys.md` gained a mode-line
section saying what it means and why there is no key for it.

**Tests.** `DocumentTest` (5) for the baseline and the undo case; `FileIoTest` (+5) for save clears, save-as moves both
marker and name, a failed save keeps the marker and reports, `loadFile` is clean, and swap recovery is dirty;
`ViewPayloadTest` (+1) for the field on the wire; `SessionsOnOneDocumentTest` (the marker test) for two windows seeing
the same marker and one save clearing both; Rust (+1 test, +2 assertions in the clip test) for where it is drawn.
238 Java, 50 + 10 Rust.

Three of those were mutation-checked: comparing by reference reddens the undo test, `markSaved` before the write reddens
the failed-save test, and `putContentAsSaved` in the recovery path reddens the recovery test.

## The bug the live check found

`ZZ` had never saved anything after an insert session. `ChangeCursorQuantumOp.getQuantumName` **peeked** at the command's
scratch stack instead of popping: when the word named a session variable rather than a quantum directly — which is
exactly what `Escape` from insert mode passes, `"tempCursorQuantum"` — it read the variable and left the word on the
stack. The next command to take an argument popped that instead, and `SaveFileOp` casts whatever is on the stack to a
path: `ZZ` was being told to save a file named `tempCursorQuantum`, whose parent is null, so it died with an unhandled
`NullPointerException` in the verticle, which also means **no repaint and no message** — a save key that silently does
nothing, in a codebase whose ticket 01 exists to make failures visible. It is fixed there (the op consumes its word
whichever way the lookup goes, and no longer NPEs when the variable has already been removed), with
`theUnsavedMarkerShowsInEveryWindowAndOneSaveClearsItEverywhere` as the test: type through a real insert session, press
`ZZ`, read the file back off disk.

Two things that follow from it, both left where they belong:

- `SaveFileOp` built its temp file with `target.getParent()`, null for a bare name, so `:w notes.txt` would have hit the
  same NPE. It resolves absolutely now. The path grammar itself is [05](05-command-key-and-grammar.md).
- **The stack is shared scratch and any command can leave words on it.** `SaveFileOp` and `LoadFileOp` read arguments
  out of it, so any leftover is a filename. Two commands clear it first (`clearEDS`, the search entries) and the rest
  are trusted to balance. 05's grammar decides what an argument is; until then this is the class of bug to expect.

## Live check, because the startup baseline cannot be tested in-process

`VISUR_FILE` is read from the environment, so no in-process test can boot with a file. The sequence was run against a
real server over a real socket (`plai!n text` is what ended up on disk):

```text
loaded a pristine file : isDirty = false
after moving, not typing: isDirty = false
after typing one char   : isDirty = true
after undo              : isDirty = false     <- the flag version lies here, every day, forever
typed it back           : isDirty = true
after ZZ                : isDirty = false, the bytes are on disk, the swap file is gone
```

A leftover swap from the run before that one was recovered at startup, and the document came up **dirty** — the
baseline rule holding at the one place nobody passes through in a test. What recovery should do about a swap older than
the file, and about the `.visur-recovery` copy it leaves in the directory forever, is
[06](06-swap-files.md)'s; it is recorded there.
