# `g` / `G` open a line above / below at line bounds

Type: task
Status: open
Blocked by: 02

## Work

`g` opens a line **above** and drops you into insert mode on it. `G` opens one **below**. `g`/`G` are free: the previous
map removed `gg`/`G` in [ticket 09](../../span-and-count/issues/09-remove-gg-and-capg.md).

**Mechanically each is one insertion and one mode change:**

- `g` inserts `"\n"` at the enclosing **line's** start;
- `G` inserts `"\n"` at the enclosing **line's** end.

**Line bounds regardless of the cursor quantum.** If the cursor quantum were consulted, `g` at the word quantum would
split the current line instead of opening one above it. A key called "open a line" that opens a line only sometimes is
worse than one that ignores the quantum, so these two read the line quantum explicitly and ignore `SPAN`, `SCOPE` and
the cursor quantum entirely.

With the separator toggle on, `G` inserts after the terminator; with it off, it inserts before it and the existing
terminator supplies the break. Both produce the same document — worth a test at both settings so the toggle is provably
irrelevant here.

**Edges that must work:** the last line of a document with **no trailing newline** (`G` still gives a new empty line);
an **empty document** (`g` and `G` both give line one, empty); a selection covering several lines (the bounds are the
enclosing line of the start for `g`, and the last line of the selection for `G`).

**The count is stashed, not read later.** `3 g` does not insert three newlines — it inserts one and repeats the *typed
text* three times, which is [05](05-insert-repeat.md). So this ticket's job for the count is only to **stash it**
alongside the `span` and `cursorQuantum` stash that `i` and `a` already make (`"span" "tempSpan" ->` in
[`InitializerService`](../../../../src/main/java/com/ple/visur/InitializerService.java), and its restore in the insert
keymap's `Escape` binding). The count must be stashed because Escape's `"navigate" changeMode` lands *before*
`CountPrefix.retireUnlessDigit` runs, so by the time the session ends the count is already gone.

**Span and cursor quantum follow `i`/`a` exactly**: stash, collapse to span 0, restore on Escape, so Escape returns you
to the selection you had.

## Done when

- `g` at the line quantum opens a line above: `"one\ntwo\n"` on line two becomes `"one\n\ntwo\n"` with the caret on the
  empty line, in insert mode.
- `G` on line one of `"one\ntwo\n"` gives `"one\n\ntwo\n"` with the caret on the empty line.
- `G` on the **last line with no trailing newline** still produces a new empty line.
- `g` and `G` on an **empty document** both leave a single empty line with the caret on it.
- `g` with the cursor quantum on **word**, caret mid-line, still opens a line above rather than splitting the current
  one — the test that pins "line-based whatever the quantum".
- `G` gives the same document with `SEP=ON` and `SEP=OFF`.
- Escape after typing restores the span and cursor quantum that were stashed on entry, and a selection survives the
  round trip.
- `3 g` inserts **one** newline and stashes a count of 3 (pinned here, consumed in
  [05](05-insert-repeat.md)).
- `keys.md` gains `g` and `G` with an example each, and says the count repeats the typed text rather than opening many
  lines.
- [ ] Manual (user-driven) check: `g` from the middle of a line, type, Escape, and confirm line breaks above are clean;
  same for `G` at the end of the document.

## Notes

- `o` is **replace mode** today. Vim fingers will reach for it wanting this, and get a mode they did not ask for.
  Deliberately untouched — keymaps are the next map's subject — but it belongs on the record rather than being
  discovered by a user.
- Inserting `"\n"` rather than a platform separator is right: `TextInsertion.insertAt` already normalises `\r\n` to
  `\n` on the way in, and the model is newline-normalised.
