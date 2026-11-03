# Visur key reference

Every key that does something in Visur today, what it is meant to do, and an example. Describes the editor
as of 2026-11-02. When a key changes, this page changes with it.

Two words carry most of the design:

- **Quantum** — a unit of text. Four exist: `character`, `word`, `line`, `document`. The *cursor quantum* is the
  unit you move and select in; the *scope quantum* is the fence a search is allowed to look inside.
- **Span** — how many quanta the selection covers. `span = 0` means no selection (a thin bar cursor); any
  positive span highlights `[cursorQuantumStart, cursorQuantumEnd)` and draws a block cursor.

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
| `h` / `l` | move one quantum left / right | `l` one character right; with `w` as the cursor quantum, one word |
| `j` / `k` | move one quantum down / up | `j` one line down |
| `ArrowLeft` / `ArrowRight` / `ArrowDown` / `ArrowUp` | same as `h` `l` `j` `k` | — |
| `q` / `w` / `e` / `r` | change the **cursor quantum**, re-deriving the selection around the cursor | `w` then `l` walks by words |
| `0`–`9` before any key | the **count** for the key that follows; digits accumulate, so `1` then `2` is twelve. Any key that is not a digit retires it, whether or not that key did anything. Only `s` takes a count so far | `12 s` twelve quanta |
| `s` | set **span** to the pending count — one with no count typed. `0 s` collapses the selection | `3 s` three quanta; `s` one; `0 s` none |
| `[` then `q`/`w`/`e`/`r` | jump to the **start** of that quantum | `[ w` start of the current word; `[ r` start of the document |
| `]` then `q`/`w`/`e`/`r` | jump to the **end** of that quantum | `] e` last character of the current line; `] r` end of the document |
| `/` then `q`/`w`/`e`/`r` | change the **scope quantum** — the fence search looks inside — and return to navigate | `/ r f foo` search the whole document for `foo` instead of only this line |
| `f` + text + `Enter` | search **forward** for a literal string, inside the scope fence | `f` `bar` `Enter` |
| `F` + text + `Enter` | search **backward** for a literal string, inside the scope fence | `F` `bar` `Enter` |
| `n` / `N` | repeat the last search in / against its previous direction | `n` next match |
| `i` | insert at the **start** of the selection; switches to insert mode | `i` |
| `a` | insert at the **end** of the selection; switches to insert mode | `a` |
| `o` | enter **replace** mode at the cursor | `o` |
| `d` | delete the selection when there is one, then re-derive the bounds at the same span | `s d` delete the quantum under the cursor |
| `y` | copy the selection to the yank register | `s y` |
| `p` | paste the yank register at the cursor | `p` |
| `g` `g` | jump to the start of the document (two-key) | `gg` |
| `G` | jump to the start of the last non-empty line | `G` |
| `;` | open **command state**: type a raw command sentence and run it | `;` `"word" quantumStart` `Enter` |
| `ZZ` | save the file | `ZZ` |

`i`, `a` and `o` all stash the current span and cursor quantum, force the span to 0 and the quantum to character,
and restore both on `Escape`.

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

Decisions recorded in [the span-and-count map](../wayfinder/span-and-count/map.md) change the following. Until
those tickets land, the table above is the truth:

- **`[` and `]` move exactly one quantum per press**; a count in front of them (`3 ]`) moves three.
  ([06](../wayfinder/span-and-count/issues/06-count-on-jumps-and-movement.md))
- **A count prefixes movement too** — `3 l` is three quanta right, `2 j` is two lines down. A count of `0` does not
  move. ([06](../wayfinder/span-and-count/issues/06-count-on-jumps-and-movement.md))
- **A count multiplies the span on operators**: `3 d` with a 3-word span deletes nine words, bounded by the scope
  fence. With no selection, `d` and `y` still do nothing. ([12](../wayfinder/span-and-count/issues/12-count-on-operators.md))
- **`d` also yanks** what it removes, so `p` can put it back. It overwrites the register. `3 p` pastes three copies.
  ([12](../wayfinder/span-and-count/issues/12-count-on-operators.md))
- **The scope fence grows to fit** a selection or operator that needs more room than it has, stepping up
  character → word → line → document. This never changes what `f` searches. ([11](../wayfinder/span-and-count/issues/11-fence-auto-promotion.md))
- **The mode line always shows scope next to span**, and the pending count while one is being typed. Today only span
  is shown. ([10](../wayfinder/span-and-count/issues/10-mode-line-scope.md))
- **`gg` and `G` are going away** in favour of `[ r` and `] r`.
  ([09](../wayfinder/span-and-count/issues/09-remove-gg-and-capg.md))
