# `g` / `G` open a line above / below at line bounds

Type: task
Status: closed
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
- [x] Manual (user-driven) check: `g` from the middle of a line, type, Escape, and confirm line breaks above are clean;
  same for `G` at the end of the document.

## Result

Shipped as `openLineAbove` / `openLineBelow` (`OpenLineAboveOp`, `OpenLineBelowOp`), bound to `g` and `G` with the same
stash-and-collapse sequence `i` and `a` use. `OpenLineTest` holds ten cases.

**The bounds are the line indices and the cursor's row, not the line quantum.** Both operators read
`getNextLineIndices()` and `getCY()` directly. That is what makes the separator toggle irrelevant here rather than merely
tested-irrelevant: there is no quantum call in the path for its inclusion of the terminator to change. `G`'s caret rule
is the one thing the toggle could not have told us either way: when the line has a newline of its own the inserted
newline lands after it and terminates the new empty line itself, so the caret goes at the insertion index; when it does
not — the last line of a document with no trailing newline — the inserted newline terminates the text line, and the
caret goes one past it, on the empty line the document now ends with. That caret is at `content.length()`, which the
model already knows as the phantom line after a trailing newline.

**"A selection survives the round trip" needed narrowing.** Escape restores the span and the cursor quantum, and
re-derives the *range* at them from wherever the typing left the caret — Escape's existing behavior for `i` and `a`,
unchanged here. Typing leaves the caret one quantum past what was typed, which after `g` is the newline that ends the
line, and a word quantum over a newline derives nothing. So the test pins what is actually promised: span 3 and the
quantum come back, and a selection is there when there is a unit under the caret (`OpenLineTest.escapeRestoresTheSpanAndQuantumThatWereStashedOnEntry`, at the character quantum).

**The count is stashed by all five insert-entering keys** — `i`, `a`, `o`, `g`, `G` — through one new word,
`stashCount`. Typing no count *removes* the stashed one, which is what keeps a `3 g` from repeating text the next
session never typed, and is why no key has to clean the variable up. Escape is untouched: putting the count back where
[05](05-insert-repeat.md) can spend it is that ticket's compile, and a stash nobody reads is harmless.

**One test from the previous map is gone.** `DocumentQuantumJumpTest.ggAndUpperGDoNothingAtAll` asserted exactly that
`g` and `G` do nothing; this ticket takes both keys, so the test went with the behavior, and `keys.md`'s "`gg` and `G`
are gone" note became "`gg` is gone" with the reason the old `G` must not come back still recorded.

Both keys arrive through both clients already — `token(KeyCode::Char(c))` in the terminal sends any plain character and
uppercase needs no handling, as `F`, `N` and `P` already showed — so neither client changed.

Remaining: the manual check below.

## Notes

- `o` is **replace mode** today. Vim fingers will reach for it wanting this, and get a mode they did not ask for.
  Deliberately untouched — keymaps are the next map's subject — but it belongs on the record rather than being
  discovered by a user.
- Inserting `"\n"` rather than a platform separator is right: `TextInsertion.insertAt` already normalises `\r\n` to
  `\n` on the way in, and the model is newline-normalised.
