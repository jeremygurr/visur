# A count repeats the insert session's text on Escape

Type: task
Status: closed
Blocked by: 04

## Work

`3 g` does not insert three newlines. It inserts one, you type once, and on Escape **what you typed is repeated**, the
way vim's `3o` works. Same for `3 G`, `3 i` and `3 a`: one mechanism, four keys.

**The rule.** A command that enters insert mode stashes its count
([04](04-open-line.md)). While that stashed count is above 1, the session accumulates **the text typed into it**. On
Escape, after the span and cursor quantum are restored, that text is inserted `count - 1` more times at the caret:

| command | between copies | `3 g` + `abc` + Escape |
|---|---|---|
| `g`, `G` | the newline the command opened with | `abc\nabc\nabc`, three lines |
| `i`, `a` | nothing | `abcabcabc`, inline |

**An empty session's unit is the break itself**, so `3 g<Esc>` and `3 G<Esc>` give three blank lines. Without that,
"`3 G` opens three lines" — what the user asked for by name — would not be true.

**A multi-line session repeats whole**: `3 G`, type `one`, Enter, `two`, Escape gives three copies of both lines. The
unit is "one insert session's worth of text"; slicing it by line would be a rule nobody could predict from watching the
screen.

**The caret ends on the last copy**, at the position Escape would have left it inside that copy — the result must be
indistinguishable from having typed it there `count` times. So `3 G`, Escape, more typing continues on the last new
line, which is the only place it can sensibly go.

**Recording costs nothing in the common path.** Accumulate typed text **only when the stashed count is above 1**, so
`g` … Escape — the 99% case — adds no state and does no work.

Recording needs to be exact rather than clever: appending on insert and popping on `Backspace` tracks the session even
when the user arrows around inside it or backspaces into text that existed before the session started. Deriving it from
"the substring between the session start and the caret" breaks on both.

## Done when

- `3 g`, type `abc`, Escape → three lines reading `abc`, caret at the end of the third. Same for `3 G`.
- `3 g<Esc>` with nothing typed → three blank lines (the empty-session rule).
- `3 i` and `3 a` → `abcabcabc` with no separator, proving the rule is the mechanism and not a `g` special case.
- A multi-line session repeats whole.
- Backspacing inside the session repeats what survives: type `abcd`, Backspace, Escape at count 3 → three copies of
  `abc`.
- Arrowing left inside the session, then typing, still repeats the text the session *inserted*, not the text it moved
  over.
- Count 1 changes nothing: `g`, `abc`, Escape is one line, exactly as before this ticket.
- `3 g` with the count already spent behaves like `g` — the stash is what reads it, and the retired count cannot leak
  in twice.
- [x] Manual (user-driven) — passed: `3 g`, type a sentence, Escape, three identical lines, no stray blank line
  between them.

## Result

Three pieces, and the count already in the model doing the work:

- **`stashCount`** (on `i`, `a`, `o`) and **`stashCountNewlineSeparated`** (on `g`, `G`) — one class, two words, because
  the only thing the five keys disagree about is what goes between the copies. Both copy the pending count into
  `tempCount` and start the recording; with a count of one or none they clear the session state instead.
- **`SessionText`** owns the three variables (`tempCount`, `tempInsertSeparator`, `tempTypedText`), the append-on-insert
  / pop-on-backspace tracking, and the repeat. `InsertCharOp`, `InsertNewlineOp` and `DeletePreviousCharOp` each call it
  once; with nothing recording, that is one failed lookup and no work.
- **`repeatSessionText`** at the end of both Escape bindings: the unit — separator, then the text — inserted
  `count - 1` times at the caret, so an empty session repeats the separator alone and `3 g` with nothing typed is three
  blank lines. `10` tests in `InsertRepeatTest`.

**This ticket found a bug that predates it.** A pending count survived into insert and replace mode, because
`retireUnlessDigit` deliberately keeps one alive while a mode other than navigate is open (the count in `3 ] w` is typed
three keypresses before the jump). Insert mode is not a sequence in flight, so `3 i` spent its three on every character
afterwards — three columns right per `insertChar`, three deleted per backspace. Nothing in the suite had typed more than
one character in a session, so it had never shown. `stashCount` now retires the count it has copied, which fixes `3 i`,
`3 a`, `3 o`, `3 g` and `3 G` in one place. `InsertRepeatTest.aCountBeforeReplaceModeIsSpentByTheKeyAndNotByTheTyping`
pins the replace-mode half.

**The copies land at the caret**, which is the rule this ticket gives, and after arrowing left the caret is in the
middle of what was typed: the repeated text is still what the session inserted (`acb` on the line, `abc` in the copies),
which is what that Done-when item is about. `ReplaceCharOp` never records, so `3 o` repeats nothing — replace mode
overwrites, and there is no session text to lay out again.

**Pinned for [06](06-undo-redo.md), as its Notes ask:** the opened newline, the typing and the repeat are one command
sequence ending in one `changeMode`, so they are **one undo unit**. Undo of `3 g` + typing removes the line, the text and
all the copies together; anything less is the bug that ticket's Notes name ("`u` will remove the words and leave the
blank line").

## Notes

- The count now means four things across the editor — multiply a span, repeat a motion, multiply an operator's extent,
  repeat an insert session. All four are vim's, all four are "do the thing more than once", and `keys.md` names them in
  one row rather than pretending there is a single rule underneath.
- Digits typed **inside** insert mode are text, not counts. Only the count typed before the entering key is stashed.
- The whole thing — the newline, the typing, the repeat — becomes **one** undo unit in [06](06-undo-redo.md), because
  the unit boundary is "entering navigate mode". This ticket should not close before that is at least pinned as an
  expected consequence, or `u` will remove the words and leave the blank line, which is precisely what nobody wants.
