# Visur key reference

Every key that does something in Visur today, what it is meant to do, and an example. Describes the editor
as of 2026-11-04. When a key changes, this page changes with it.

Two words carry most of the design:

- **Quantum** — a unit of text. Four exist: `character`, `word`, `line`, `document`. The *cursor quantum* is the
  unit you move and select in; the *scope quantum* is the fence a search is allowed to look inside.
- **Frame** — what `[` and `]` walk to the edge of: the coarser of the cursor quantum and the quantum key you typed,
  ranked by nesting and never by how much is currently selected. With the cursor quantum on lines, `] q` goes to the
  end of the line and selects the characters there.
- **Span** — how many quanta the selection covers. `span = 0` means no selection (a thin bar cursor); any
  positive span highlights `[cursorQuantumStart, cursorQuantumEnd)` and draws a block cursor. Only `s` writes it, so
  moving onto a blank line at the word quantum selects nothing and *keeps* the number; moving back selects again. The
  same is true wherever the quantum simply isn't under the cursor: an indent, or the space between two words.

Quantum keys are shared by every command that takes one:

| key | quantum |
|---|---|
| `q` | character |
| `w` | word |
| `e` | line |
| `r` | document |

## Navigate mode

| key | does | example |
|---|---|---|
| `h` / `l` | move one quantum left / right, or `count` of them | `l` one character right; `3 l` three; with `w` as the cursor quantum, words |
| `j` / `k` | move one quantum down / up, or `count` of them | `j` one line down; `2 j` two lines, same column |
| `ArrowLeft` / `ArrowRight` / `ArrowDown` / `ArrowUp` | same as `h` `l` `j` `k` | — |
| `q` / `w` / `e` / `r` | change the **cursor quantum**, re-deriving the selection around the cursor | `w` then `l` walks by words |
| `0`–`9` before any key | the **count** for the key that follows; digits accumulate, so `1` then `2` is twelve. It is spent by the command it belongs to — including a two-key command — and any other key drops it. `0` means none: `0 l` does not move, `0 s` selects nothing. While it is pending the mode line shows `COUNT=n`. A count spent on an insert-entering key is kept for the session it opens, where it repeats the typing instead | `12 s` twelve quanta; `3 ] w` three words |
| `s` | set **span** to the pending count — one with no count typed. `0 s` collapses the selection | `3 s` three quanta; `s` one; `0 s` none |
| `\` | toggle **separators** — whether a selection includes what follows it. Shown as `SEP=ON` / `SEP=OFF` in the mode line | `s` on a line includes its newline; after `\` the highlight stops at the last character |
| `[` then `q`/`w`/`e`/`r` | jump to the **start** of that quantum — one quantum per press, whatever the span | `[ w` start of the current word; `[ r` start of the document |
| `]` then `q`/`w`/`e`/`r` | jump to the **end** of that quantum, `count` of them | `] e` last character of the current line; `] r` end of the document; `3 ] e` of the third line |
| `/` then `q`/`w`/`e`/`r` | change the **scope quantum** — the fence search looks inside — and return to navigate | `/ r f foo` search the whole document for `foo` instead of only this line |
| `f` + text + `Enter` | search **forward** for a literal string, inside the scope fence | `f` `bar` `Enter` |
| `F` + text + `Enter` | search **backward** for a literal string, inside the scope fence | `F` `bar` `Enter` |
| `n` / `N` | repeat the last search in / against its previous direction | `n` next match |
| `i` | insert at the **start** of the selection; switches to insert mode | `i` |
| `a` | insert at the **end** of the selection; switches to insert mode | `a` |
| `o` | enter **replace** mode at the cursor | `o` |
| `g` | open a line **above** the one the cursor is on, and type on it | `g` mid-word still opens a line above, never splits the line it is in |
| `G` | open a line **below** the one the cursor is on, and type on it | `G` on the last line of a document with no trailing newline still gives a new empty line |
| `d` | delete the selection, taken `count` times, leaving the highlight as the gap it made — the span survives, and the next move re-derives the window from it. What it removed goes to the yank register, so `p` puts it back. Nothing selected, or a count of zero, and nothing happens | `s d` delete the quantum under the cursor; `3 s` then `3 d` delete nine |
| `y` | copy the selection, taken `count` times, to the yank register | `s y`; `2 s` then `3 y` copy six quanta |
| `p` | **replace the selection** with the yank register, `count` copies, and put what the paste displaced into the register. Over nothing it displaces nothing, so the register keeps what it held and `p p` pastes twice. The caret ends on the last pasted character, span 0 | `s y` `] e` `p` move a quantum; `3 p` three copies over one selection |
| `P` | insert the yank register at the **start** of the selection without deleting anything; the selection keeps covering the same text, shifted right | `P` with three words selected puts the register in front and leaves those three selected |
| `u` | undo **one command** — the whole command, so an insert session comes back with the line it opened, and everything a count repeated comes back at once. Movement is not an edit: `u` after `5 l` walks back to the last edit, cursor and all. Nothing to undo is a no-op | `s d` then `u`; `3 g`, type, `Escape`, then `u` |
| `R` | redo one command. A new edit clears what `R` can walk forward to, and the top of the stack is a no-op | `u` then `R` |
| `;` | open **command state**: type a raw command sentence and run it | `;` `"word" quantumStart` `Enter` |
| `ZZ` | save the file | `ZZ` |

`i`, `a`, `o`, `g` and `G` all stash the current span and cursor quantum, force the span to 0 and the quantum to
character, and restore both on `Escape`.

**Both line-opening keys read the line the cursor sits on**, not the cursor quantum, `SPAN` or `SCOPE`, and the same
document comes out whether the separator toggle is on or off.

**Undo is snapshots, and a command is the unit.** The boundary is entering navigate mode, so `g`, everything typed and
the count's repeat are one `u`. Undo puts the content, the cursor and the span back — **never the yank register**:
undoing a paste leaves the register holding the text that paste displaced, because a register restored from a snapshot
can name text that is no longer in the document. Loading a file empties the history, so `u` cannot paste one file into
another. Redo is `R` rather than `Ctrl-r` because the browser sends `Ctrl-r` as plain `r` and reloads the page with it,
and the terminal drops modifier chords on purpose.

**A count in front of an insert-entering key repeats what you type**, vim's `3o`: `3 g` opens **one** line rather than
three, you type once, and `Escape` writes the other copies. `g` and `G` separate the copies with the newline they
opened the line with, so `3 g` with nothing typed is three blank lines; `i`, `a` and `o` separate them with nothing, so
`3 i` then `abc` gives `abcabcabc`. A session that typed across a newline repeats whole, a `Backspace` takes the
character out of what gets repeated, and the copies go in at the caret. Only the count typed *before* the key counts:
digits typed inside the session are text.

**`gg` is gone**, along with the only hand-written two-key sequence in the server: `[ r` and `] r` reach both ends
through the document quantum. `G` came back as open-a-line-below; recorded so nobody puts the *old* `G` back: it landed
on the *start of the last non-empty line*, and `] r` lands on the *end of the document* — on a document that does not
end in a line break those are different characters, and the second is what the quantum model means by "the end".

## Insert mode

| key | does | example |
|---|---|---|
| any single character | insert it and move right one quantum | `x` |
| `Enter` | insert a newline | — |
| `Backspace` | delete the character before the cursor and step back | — |
| arrows | move without editing | — |
| `Escape` | leave insert mode, restoring the span and cursor quantum saved on entry | — |

## Replace mode

| key | does | example |
|---|---|---|
| any single character | overwrite the character under the cursor and move right | `x` |
| `Enter` | move down one line (it does **not** insert a newline) | — |
| `Backspace` | move left one quantum | — |
| arrows | move without editing | — |
| `Escape` | leave replace mode, restoring the saved span and cursor quantum | — |

## Search submode (after `f` or `F`)

| key | does |
|---|---|
| any single character | append it to the search target |
| `Backspace` | remove the last character of the search target |
| `Enter` | run the search and return to navigate |
| `Escape` | abandon the search and return to navigate |

## Command state (after `;`)

| key | does |
|---|---|
| any single character | insert it into the command line at the cursor |
| `ArrowLeft` / `ArrowRight` | move within the command line |
| `ArrowUp` / `ArrowDown` | walk command history (20 entries deep) |
| `Backspace` | delete a character from the command line |
| `Enter` | compile and run the command, push it onto history |
| `Escape` | leave command state without running |

## Terminal client only

Intercepted by the terminal before the server sees them:

| key | does |
|---|---|
| `ZZ` | save and detach, leaving the server running |
| `ZQ` | detach, leaving the server running |
| `ZD` | quit, stopping a server the launcher started |

`Ctrl-C` is a byte in raw mode, not a signal, which is why quitting needs its own binding.

## Selections and separators

**A selection covers the unit and whatever joins it to the next one**, so `s` on a line highlights one cell past the end
of the row — the newline — and `d` on it leaves no blank line behind. A word takes the whole run of spaces and tabs after
it but never a newline; the character and document quantums have no separator. `\` turns the rule off, and it changes
bounds only: span, cursor quantum and search are untouched by the toggle.

Counting already means four things, and vim has all four: it multiplies a span (`3 s`), repeats a motion or jump
(`3 l`, `3 ] e`), multiplies an operator's extent (`3 d`), and repeats an insert session (`3 g`).

## The mode line

The last row names the mode and then the state the keys are working on: the cursor quantum, `SCOPE=`, `SEP=`, `SPAN=`,
and `COUNT=` while a count is pending. **A `*` stuck to the mode word means the text holds work no file has**:
`NAVIGATE MODE*`. It arrives when you type and leaves when you save — including when an `u` puts back exactly what the
file already had, which is why it compares against the saved text rather than remembering that you edited. It is glued
to the mode word because everything right of it is what a narrow window clips and what an error message overwrites, and
an unsaved file is the last thing a window should be made to stop saying.

There is no key for the marker and there must not be one: it states a fact about the data, and a fact the user can
toggle is a fact the user will disbelieve.

## Errors

**When something you asked for fails, the mode line says so**, prefixed with `!`: `! loadFile: file not found: /tmp/gone.txt`.
It is the only thing on that line that describes what just happened rather than what mode you are in, so in the terminal it
takes the tail — the quantum, `SCOPE=`, `SEP=` and `SPAN=` make room for it, and the mode word stays. A message is retired
by the next keypress or paste, including after a failed save: it reports the last thing you asked for, and once you ask for
something else it is stale. Nothing is reported by any other channel; the server log still has the same text, for a bug
report rather than for you.
