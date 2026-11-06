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

- Typing sets dirty; `ZZ`/`:w` clears it; the marker is asserted through the view payload, in both clients' mode lines.
- **`d` then `u` clears the marker** — the document matches the file again, and the test says so explicitly, because a
  future "optimisation" to a flag will reintroduce the lie.
- A document loaded from disk is clean. A document saved to a *new* path via `:w other.txt` is clean and its baseline is
  now that file.
- An empty unnamed document is clean; the same document with one character typed is dirty.
- Saving to an unwritable path leaves the document **dirty** and raises [01](01-message-surface.md)'s message. A
  failed save that clears the marker is the worst bug this ticket could ship.
- The marker survives the 40-column clip, asserted by extending the existing clip test rather than a new one.
- `:open` and `:q` read this and nothing else — no second notion of unsaved-ness appears in [05](05-command-key-and-grammar.md).

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
