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
| `0`–`9` before any key | the **count** for the key that follows; digits accumulate, so `1` then `2` is twelve. It is spent by the command it belongs to — including a two-key command — and any other key drops it. `0` means none: `0 l` does not move, `0 s` selects nothing. While it is pending the mode line shows `COUNT=n` | `12 s` twelve quanta; `3 ] w` three words |
| `s` | set **span** to the pending count — one with no count typed. `0 s` collapses the selection | `3 s` three quanta; `s` one; `0 s` none |
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
| `;` | open **command state**: type a raw command sentence and run it | `;` `"word" quantumStart` `Enter` |
| `ZZ` | save the file | `ZZ` |

`i`, `a`, `o`, `g` and `G` all stash the current span and cursor quantum, force the span to 0 and the quantum to
character, and restore both on `Escape`.

**Both line-opening keys read the line the cursor sits on**, not the cursor quantum, `SPAN` or `SCOPE`, and the same
document comes out whether the separator toggle is on or off. A count in front of either — `3 g` — opens **one** line,
not three: the count is stashed for the session, and
[05](../wayfinder/separators-and-undo/issues/05-insert-repeat.md) is what will spend it by repeating what you type.

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

## In flight

Decisions recorded in [the separators-and-undo map](../wayfinder/separators-and-undo/map.md) change the following.
Until those tickets land, the table above is the truth:

- **A selection will include the separator after it.** `s` on a line will highlight one cell past the end of the row,
  standing for the newline, so `d` on a line will leave no blank line behind. Word selections take the whole run of
  spaces or tabs after them, but never a newline. `\` will turn this off, shown as `SEP=ON` / `SEP=OFF` beside the span.
  ([01](../wayfinder/separators-and-undo/issues/01-editor-spec.md),
  [02](../wayfinder/separators-and-undo/issues/02-selection-includes-separator.md))
- **A count before an insert-entering key will repeat the typed text**, vim's `3o`: `3 g`, type once, Escape gives
  three identical lines — not three newlines. ([05](../wayfinder/separators-and-undo/issues/05-insert-repeat.md))
- **`u` and `R`** will undo and redo a whole command. Undo will restore the selection, never the yank register.
  ([06](../wayfinder/separators-and-undo/issues/06-undo-redo.md))

Counting already means four things, and vim has all four: it multiplies a span (`3 s`), repeats a motion or jump
(`3 l`, `3 ] e`), multiplies an operator's extent (`3 d`), and will repeat an insert session (`3 g`).
