# A count repeats the insert session's text on Escape

Type: task
Status: open
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
- [ ] Manual (user-driven) check: `3 g`, type a sentence, Escape, and confirm three identical lines with no stray blank
  line between them.

## Notes

- The count now means four things across the editor — multiply a span, repeat a motion, multiply an operator's extent,
  repeat an insert session. All four are vim's, all four are "do the thing more than once", and `keys.md` names them in
  one row rather than pretending there is a single rule underneath.
- Digits typed **inside** insert mode are text, not counts. Only the count typed before the entering key is stashed.
- The whole thing — the newline, the typing, the repeat — becomes **one** undo unit in [06](06-undo-redo.md), because
  the unit boundary is "entering navigate mode". This ticket should not close before that is at least pinned as an
  expected consequence, or `u` will remove the words and leave the blank line, which is precisely what nobody wants.
