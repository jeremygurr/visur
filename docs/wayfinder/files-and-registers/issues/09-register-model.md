# `|` selects a register: `|0` is the clipboard, `|1`–`|9` rotate, `|a`–`|z` do not

Type: task
Status: open
Blocked by: 03, 08

## Work

There is one register today. `y` writes it (`YankSpanOp`), `d` yanks what it removes, `p` replaces the selection with it
and takes the displaced text into it, `P` inserts it without disturbing the selection. This ticket makes the register a
**choice**, and the rule that has to survive every part of it is: **`y`, `d`, `p` and `P` behave identically no
matter which register is selected** — the only key a user learns is `|`.

**The slots.**

- **unnamed** — what `y`/`d` write and `p` reads, unless the user selects something. This is the register today's tests
  describe, so it stays where it is.
- **`|0` — the system clipboard**, via [08](08-clipboard-transport.md). Writing it is one OSC 52 write per command; reading
  it is the cached-then-ask path from that ticket, and a failed read is a message and **no paste**.
- **`|1`–`|9` — the rotation.** Every yank, delete and `p`-displacement overwrites 1 and pushes 1→2, 2→3, and so on, with
  9 falling off. So 1 is "the thing you had before this one", which is the case that actually comes up: two values you
  need to alternate between are always in 1 and 2.
- **`|a`–`|z` — named slots.** Nothing rotates them and nothing overwrites them except an explicit yank into a selected
  slot. They are what "keep this out of the way" means, and they make a general lock unnecessary.
- **A freeze toggle** stops the rotation from touching 1, for the user who wants the newest value pinned without picking a
  letter every time. One key, one mode-line glyph, and it is the *only* mode-line state in this map that is a preference
  rather than a fact about the document.

**Selection is sticky and global.** `|3` selects until the next `|`; nothing about a command consumes it. It is global
because there is one terminal emulator, which is the premise for `|0` meaning anything at all — the map's out-of-scope
section says what breaks if that stops being true.

**`|` opens a submode**, the same shape as `[` and `]` (`"quantumStart" pushSubmode`), whose keymap binds `0`–`9` and
`a`–`z`. That precedent matters: inside the submode a digit cannot be mistaken for a count, and the count machinery
(`CountPrefix.retireUnlessDigit`, which deliberately leaves a pending count alive outside navigate mode — the thing that
broke `3 i`) never sees it. `|` is unbound today, in both clients.

**`p`'s displaced text goes to the selected register, including when it is `|0`.** Chosen knowingly: with the clipboard
selected, pasting over a paragraph puts text you never copied into your real clipboard. The alternative — a register you
paste *from* that is not the register you paste *into* — is worse, and it would break the "no behaviour should feel
different" rule this feature was specified with.

## Done when

- `|3` then `y` then `|1` then `p` pastes what was yanked, and `p` twice more still pastes it: selection is sticky and
  reading a register does not consume it.
- Rotation is asserted with a sequence, not a description: yank A, yank B, delete C → `|1` holds C, `|2` holds B, `|3`
  holds A. Then yank D and 9 slots shift by one.
- **`p` over a selection rotates the displaced text** — that is the user's stated case: "if you paste over something then 2
  should have your old paste and 1 will have the new."
- `|a` yank, then four yanks, then `|a` paste returns the original. The rotation demonstrably does not touch named slots.
- The freeze toggle: on, then three yanks, then `|1` still holds what it held when it was frozen.
- `|0` + `y` causes exactly one clipboard write for the command (`3 d` is one, asserted), and `|0` + `p` pastes the cached
  value with the client offering no read.
- `|0` + `p` when the clipboard is unavailable from the transport shows the message and **changes nothing**.
- Existing register behaviour is untouched where no register is selected: every test written by the
  [separators-and-undo](../../separators-and-undo/map.md) map — `p` replaces the selection, `p` takes what it displaced,
  count multiplies copies while the register records displacement once — passes unchanged. **If one of those needs an
  edit, stop and say why.**
- `keys.md` documents `|`, the slots, what rotates, what `|0` can and cannot do over SSH, and the freeze glyph.

## Notes

- Registers are **global state**, which cuts against [03](03-document-session-split.md)'s document/session classification
  and is right anyway: they are not per-document and, with one terminal emulator, not per-session either. Record that in
  the classification table rather than leaving a third category unexplained.
- The rotation must not be implemented by re-reading a register list on every yank. One `rotate()` on an array, with
  `|0` and `|a`–`|z` outside it, and the tests above become three lines each.
- **`|0`'s text is not stored as an ordinary register.** The cache of "last thing we wrote" is a clipboard detail; keeping
  it in the same slot type as visur's registers is how you end up writing the user's clipboard on undo, or rotating the
  clipboard, which is a thing nobody would choose if they had to say it out loud.
- Undo and registers: `u` restores content, cursor and span, and **never** the register — that asymmetry is pinned by
  `UndoRedoTest.undoLeavesTheRegisterAlone`, and it survives this ticket unchanged. A register selector makes it more
  visible, not less true.
- Separator toggle decides what a line yank contains, so `|0` + `y` at the line quantum with `SEP=ON` puts a trailing
  newline in the system clipboard. That is honest — `SEP=ON` is on the mode line precisely so you can see it — and it gets
  one test so nobody "fixes" it by trimming.
- Whether `|` should also work as a *prefix* (`|2y`, vim's `"2y`) is undecided and out of scope: the sticky selector covers
  the use case with two keystrokes instead of one, and a prefix grammar is a change to how every operator reads state.
