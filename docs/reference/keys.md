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
| `p` | **replace the selection** with the yank register, `count` copies, and put what the paste displaced into the register. What was selected stays selected, and what is selected is now the text that was pasted — counted in characters, the one quantum that can end where a paste ends. Over nothing it displaces nothing, so the register keeps what it held and `p p` pastes twice | `s y` `] e` `p` move a quantum; `3 p` three copies over one selection |
| `P` | insert the yank register at the **start** of the selection without deleting anything; the selection keeps covering the same text, shifted right | `P` with three words selected puts the register in front and leaves those three selected |
| `u` | undo **one command** — the whole command, so an insert session comes back with the line it opened, and everything a count repeated comes back at once. Movement is not an edit: `u` after `5 l` walks back to the last edit, cursor and all. Nothing to undo is a no-op | `s d` then `u`; `3 g`, type, `Escape`, then `u` |
| `R` | redo one command. A new edit clears what `R` can walk forward to, and the top of the stack is a no-op | `u` then `R` |
| `:` | open **command state**: type a command and run it — `:w`, `:open path`. `;` used to be this key and now does nothing at all | `:` `w` `Enter` |
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

## Command state (after `:`)

| command | does |
|---|---|
| `:w` | write the file the document came from |
| `:w path` | write there, and make it the file the document came from |
| `:open path` | load a file. Refused while there is unsaved work — `:w` first. If a swap says the file is not the whole story, it stops and asks |
| `:q` | leave this window. Refused while there is unsaved work |
| `:q!` | leave, and throw the unsaved work away — its swap file too, so it does not come back at the next boot |

**:q closes the window, not the server.** It leaves through the same door as the terminal's `ZQ` — the socket closes
normally, the client exits 0, the launcher keeps the server running — and every other window keeps editing. Stopping the
process stays `ZD`'s: the launcher started it, a browser tab has no business ending a process it cannot see, and a
command that ends other people's sessions is not a quit.

**A path is the rest of the line.** No quotes, no escaping: `:open ~/notes/ideas.txt` is one path. Quotes still work and
now mean exactly one thing — keep this space, including at the end — so a path whose last character is a space cannot be
typed without them. Trailing spaces you did not mean are trimmed, because a file called `notes.txt ` is a bug report.

A word that is not a command says so on the mode line, naming itself. It used to compile to nothing at all, which looked
exactly like a command that had worked. `:w` with nothing to write to says `Error writing: No filename provided`.

**The raw sentence door is gone.** `;` used to take an editor sentence — `;"word" quantumStart` — and the four commands
above are a language of their own instead. Keys are the rest of the interface.

| key | does |
|---|---|
| any single character | insert it into the command line at the cursor |
| `ArrowLeft` / `ArrowRight` | move within the command line |
| `ArrowUp` / `ArrowDown` | walk command history (20 entries deep) |
| `Backspace` | delete a character from the command line |
| `Enter` | compile and run the command, push it onto history |
| `Escape` | leave command state without running |

## Opening a file

| you type | you get |
|---|---|
| `visur` | whatever the server on `:8888` already has |
| `visur ~/notes/ideas.txt` | that file, from any directory — the path is resolved where you typed it, not where the server was started |
| `:open notes.txt` | that file, in the window you are typing in |

The file you name travels with the window you are about to open — a query on its socket — rather than with the server,
so `visur path` means the same thing against a server that started two seconds ago and one that has been up since
morning. One thing stops it: a window already editing. There is one document per process, so the newcomer is told why on
its own message line, where it can read it, instead of in a shell where the answer was the same whether the old server
was idle or busy. Exit 2 means the command line was a flag, which is not a file either.

A path that is not there is a document you are about to write when you name it to `visur`, and a refusal when you type
`:open`. The same missing file, the opposite answer, and the difference is that inside a session there is already a
document standing where the typo would go.

## Swap files and recovery

A document with work in it is mirrored to disk, at most twice a second. The last half second of typing is what a crash
costs, which is the price of not writing a file on every keystroke.

| the document | its swap is |
|---|---|
| opened from `notes.txt` | `.notes.txt.swp`, beside the file |
| never given a name | a slot from a pool: `.swa`, `.swb` … `.swz`, then `.swa1`, and so on |

A pool slot is claimed by existing, so two windows reaching for the pool at once cannot both be told they got `.swa`,
and a slot is never overwritten. A document keeps the slot it got, so the nameless buffer you worked on for an hour is
still `.swa` tomorrow. The pool is in the directory the terminal was launched in — a browser tab has no directory of its
own, so its slots go to `~/.local/state/visur/browser/` — and the first window to type decides which, because a nameless
buffer has one swap however many windows are looking at it. A `.swa` in `git status` is your own unsaved buffer and not
corruption; `-Dvisur.swapDir=/path` moves the pool somewhere else.

A slot goes away when a save gives the buffer a name, and when `:q!` closes the **last** window on that document.
`:q!` in one tab leaves it for the window that stayed, since the swap belongs to the document and `:q!` closes a window.

**Nothing is recovered without being asked.** A booting server loads the file it was given and pays no attention to any
swap. `:open path` is where a swap gets answered, and the open waits for the answer rather than opening and complaining
afterwards, because afterwards there is only one version left to choose:

| key | means |
|---|---|
| `r` | recover: the swap's text comes into the buffer, and it comes in unsaved — that is what a swap is |
| `d` | delete: that work is rejected, the swap goes with it, and the file opens clean |
| `o` | read-only: open the file and look; `:w` refuses, `:w path` writes where you tell it |
| any other key, `Escape` | change nothing — the file stays closed, the swap stays where it is, the question goes away |

While visur is asking, the keyboard belongs to the question: the three keys answer it and every other key is the safe
answer, so a stray key cannot choose a version of a file for you.

## Terminal client only

Intercepted by the terminal before the server sees them:

| key | does |
|---|---|
| `ZZ` | save and detach, leaving the server running |
| `ZQ` | detach, leaving the server running |
| `ZD` | quit, stopping a server the launcher started |

`Ctrl-C` is a byte in raw mode, not a signal, which is why quitting needs its own binding.

## The clipboard

visur never touches an OS clipboard: it **asks the client**, because over SSH the client is the machine holding both the
clipboard and the keyboard. Two questions go down the socket — write this text, and what does your clipboard hold — and
an answer comes back up.

**Copying out works over SSH** on the mainstream emulators (OSC 52, buffer `c`, the text base64'd). A yank, a delete and
the text a paste covered are one request per command rather than one per keystroke, sent to the window whose keys made
them. A copy bigger than 56 000 bytes is refused with a message instead of truncated — half a clipboard is worse than
none, and the yank itself still stands, so `p` still works inside visur.

**Copying in depends on the terminal, and mostly does not work.** Terminal.app has no OSC 52 read, iTerm2 gates it
behind a preference, tmux swallows it, and visur's own terminal emulator — where both directions work — is the plan and
not the present. So when the clipboard cannot be read the paste **does not happen**, and the window says
`clipboard: cannot read it; paste with your terminal's own key`. That refusal also discards what visur remembers of the
last thing it copied: a memory it can no longer check is a guess, and a guess pasted quietly into a document is the one
answer worse than an error.

Until then, paste with your terminal's own paste key. Bracketed paste arrives as text and needs no OSC 52 at all.

## Selections and separators

A paste that covered a selection leaves that selection lying over the text it inserted, so a second `p` is a paste onto a
selection and puts back what the first one displaced. Over nothing, `p p` is still two copies.

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
