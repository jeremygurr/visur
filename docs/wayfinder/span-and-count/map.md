# Map: span-and-count

## Destination

Visur's selection is **larger than one thing**, and a command can be **repeated by typing a number**.

Concretely: `3 s` selects three quanta of whatever the cursor quantum is, `] w` lands at the end of the next word
rather than the third, `3 d` deletes nine words when the span is three words, the text it removed is pasteable with
`p`, and the mode line tells you the size of the selection and the size of the fence it lives inside at all times.

This closes the oldest known feature gap in the repo: "**Span is binary.** … a multi-quantum span cannot be
expressed" ([renderer-delivery/map.md](../renderer-delivery/map.md), [terminal-build/map.md](../terminal-build/map.md)).

## Notes

- **The clamp is one line, the model is not.** [`SetSpanOp`](../../../src/main/java/com/ple/visur/SetSpanOp.java)
  clamps with `spanToSet > 0 ? 1 : 0`, but every quantum already accepts a `span` parameter in
  `getBoundaries(ca, nl, span, includeTail)`, and [`RegexQuantum`](../../../src/main/java/com/ple/visur/RegexQuantum.java)
  already loops over it. The work is making the other three quanta honest about a parameter that was always in the
  signature.
- **Two words, two meanings — one of them gets renamed.** The model's `scopeQuantum` (set by `/`, read by search)
  and the local `scopeQuantum` in [`Quantum.getScopeAndCursorQuantums`](../../../src/main/java/com/ple/visur/Quantum.java)
  (computed per `[`/`]` press by comparing two quanta's bound lengths) are unrelated objects that share a name. The
  local one becomes `frameQuantum`; the search fence keeps its name.
- **`scope` gains a second job.** It was only a search fence. Here it also becomes the ceiling on operators — and
  because a fence that is too small to contain a selection is useless, it grows to fit. That growth is
  **transient**: it never writes `scopeQuantum`, because silently widening what `f` searches is a worse surprise
  than a wide selection.
- **The selection model is a window, not a rubber band.** A movement with span 3 and count 3 slides a three-unit
  window three units. It never grows the selection, and neither does `d` or `y` collapse it.
- **Tests go through a pure seam, not the live editor.** `getBoundaries` reads `ServiceHolder.editorModelCoupler`
  statics (Vert.x `SharedData` maps) which no test can boot cheaply. Ticket 01 extracts static bound functions so
  the arithmetic is testable the way [`DocumentJumpOpTest`](../../../src/test/java/com/ple/visur/DocumentJumpOpTest.java)
  already tests `bottomLineStart(String)`. Ticket 01 lands first for this reason, not as a courtesy refactor.
- **Skills to consult**: `tdd` for tickets 01–04 and 11–12 (all pure arithmetic once the seam exists),
  `codebase-design` if ticket 01's seam starts wanting to be an interface, `wiki-lint`-style doc care when the key
  reference changes.

## Decisions

Settled in the design session of 2026-11-02. Each is implemented by a ticket, not assumed.

- **Span counts quanta, contiguously.** Span N covers the first through the Nth unit of the cursor quantum,
  including any text between them, as one `[start, end)` range. The window slides on movement.
- **No upper bound on the requested span.** A span larger than the document is legal and is *stored* as requested;
  only the selection clamps, and only at the end of the content. The reason is a feature, not an oversight:
  documents are expected to multiply, and "not enough" is then a temporary condition.
- **Overflow continues in document order.** Units keep counting past a line break; nothing is clipped at a scope
  boundary. This is the same observable behavior as fence auto-promotion, stated from the other side, and the
  tickets say so once rather than twice.
- **Fence promotion is by rank, and just enough to fit.** `character → word → line → document`, measured from where
  the cursor actually is, not from the fence's worst case. A word never straddles a newline (`\S+`), so the ladder is
  structurally sound; a `character` or `word` fence cannot hold a multi-unit selection at all and promotes at once.
- **Counts precede the key**, vim-style: `12 s`, `3 ]`, `2 j`. The digit-after-`s` submode is deleted rather than
  kept as a synonym.
- **A count is consumed by the first non-digit key** and does not survive it. `0` on movement is a no-op.
- **Counts multiply span on operators.** `3 d` at span 3 words deletes nine. At span 0, `d` and `y` still do nothing:
  this editor deletes only what it has shown you.
- **`[` and `]` move one quantum per press**, with a count for more, and choose their frame **structurally at span
  1** so `] w` does not change meaning when the selection gets bigger.
- **Movement ignores the fence.** Search, operators and paste respect it; `hjkl` do not.
- **`d` yanks.** The register is clobbered unconditionally — named registers are a separate idea.
- **`gg` and `G` are deleted**, in favour of `[ r` and `] r`. Accepted loss: `G` reached the *start of the last
  non-empty line*, `] r` reaches the *last character*.
- **Renderers read `span > 0`, never `span == 1`,** and the mode line always shows span *and* scope.
- **Only `s` writes the span.** The operators that recompute a range — move, change quantum, delete — leave the number
  alone. Added 2026-11-03 after the collapse rule was found to be a trap door: at the word quantum a blank line yields
  an empty range, the shared `SpanCollapse` (now deleted) took the span to zero with it, and a range derived from span
  zero is always empty, so the selection could never come back. A number the user typed outlives the content it was
  typed against; `0 s` is the only way to ask for no selection.

## Decisions so far

<!-- one line per closed ticket: gist, then link to the ticket holding the detail -->

- [Pure bound seam](issues/01-bound-seam-and-tests.md): every quantum's bounds are a static function of
  `(content, nl, ca, cy, span)`, 42 tests at that seam, and "a collapsed range leaves no span" was one method called
  from four operators instead of three hand-written copies — that method is since deleted, see the rule above. Two findings: span 0 on the character quantum used to mean
  a one-character selection, which quietly moved where `p` inserts and where `n` starts searching (both now agree with
  the drawn caret); and at span 0 `] r` lands at the *start* of the document, because the document quantum parks the
  cursor there — [ticket 09](issues/09-remove-gg-and-capg.md) owns that.
- [`SetSpanOp` stops clamping](issues/04-setspanop-no-clamp.md): span keeps the number it was given and the range
  clamps at the content end, so `s 3` selects three quanta today. `d` and `y` needed no change — neither wrote span —
  and the `tempSpan` stash across insert mode turns out to be magnitude-blind. Two input gaps left for
  [05](issues/05-count-prefix.md): nine is the ceiling, and bare `s` still does nothing rather than selecting one.
- [Count prefix](issues/05-count-prefix.md): digits go before the key, so `12 s` selects twelve quanta, and the
  `s`-then-digit submode is deleted. It is not a `KeymapHandler`, as this ticket planned — the keymap only consults
  handlers on a miss, so a handler could not clear the count for the bound keys; it is a static either side of the
  keypress in the verticle instead. **A pending count is still invisible** — the state is in the model, and
  [10](issues/10-mode-line-scope.md) puts it on the mode line.
- [Count reaches movement and jumps](issues/06-count-on-jumps-and-movement.md): `3 l` is three quanta, `2 j` two lines,
  `0 l` nowhere, and `]` walks count quanta while the span only decides how wide the selection lands. Two decisions
  beyond the ticket: **a count outlives the keypress that opens a submode**, because `] w` is one command and `]` was
  eating the count; and **`0 ]` is `]`**, since a jump's arithmetic cannot express zero units. Scaling the movement
  vector, as this ticket sketched, does nothing — the character quantum reads only its sign — so the count loops the
  call instead.
- [Frame ranked at span 1](issues/07-frame-at-span-one.md): `] w` leaves the cursor in the same quantum whatever the
  span, and the locals in `Quantum.java` say *frame* — the search fence keeps the name *scope*. Found on the way: the
  word quantum's walk advances **one character per remaining span** across whitespace, so twenty words can never
  out-measure twenty lines and the ranking example in this ticket's text is unreachable until
  [02](issues/02-span-per-quantum.md) fixes that walk. The inversion that is reachable today is `character` versus
  `word` across a run of blank lines, where the two measure equal at span 20 and tie for the frame.
- [Terminal block cursor at `span > 0`](issues/08-terminal-block-cursor.md): the terminal chose its cursor shape with
  the value that used to be the only nonzero one, so it shipped a bar over a three-cell highlight. Landed with 04
  rather than after it. Its amendment turned out to be five documents, not one: the conformance row and four places in
  the spec that read "0 or 1 today".

## Tickets

| # | title | blocked by |
|---|---|---|
| ~~01~~ [closed](issues/01-bound-seam-and-tests.md) | Pure bound seam + boundary tests | — |
| [02](issues/02-span-per-quantum.md) | Span > 1 in each quantum's bounds | 01 |
| [03](issues/03-overflow-order.md) | Overflow counts in document order | 02 |
| ~~04~~ [closed](issues/04-setspanop-no-clamp.md) | `SetSpanOp` stops clamping; span survives `d` and `y` | 02 |
| ~~05~~ [closed](issues/05-count-prefix.md) | Count prefix in navigate mode; delete the `s`-then-digit submode | — |
| ~~06~~ [closed](issues/06-count-on-jumps-and-movement.md) | Count reaches `[` `]` and movement | 05, 07 |
| ~~07~~ [closed](issues/07-frame-at-span-one.md) | Frame chosen structurally at span 1; rename the `Quantum.java` locals | — |
| ~~08~~ [closed](issues/08-terminal-block-cursor.md) | Terminal block cursor at `span > 0` | — |
| [09](issues/09-remove-gg-and-capg.md) | Remove `gg` / `G` / `DocumentJumpOp` | — |
| [10](issues/10-mode-line-scope.md) | Mode line always shows scope next to span | — |
| [11](issues/11-fence-auto-promotion.md) | Fence auto-promotion, transient | 02, 07 |
| [12](issues/12-count-on-operators.md) | Count × span on `d` / `y` / `p`; `d` yanks | 05, 11 |

Read the graph this way: **01 → 02** is the spine (selection arithmetic), **05** is an independent spine (input), and
**03, 04, 06, 11, 12** hang off whichever spine they need. **07, 08, 09, 10** touch nothing shared and can be taken in
any order, including first.

## Not yet specified

- **Named registers.** `d` clobbering `y` is accepted for now, but the first user who yanks something, deletes, and
  wants the yank back has found the next ticket. Whether registers are a model var per name or a stack is unasked.
- **Multi-document spans.** Every rule here is written to survive several documents (`no upper bound`, overflow
  "in document order"), and the seam in ticket 01 is where the next rank would go, but the fence above `document`
  does not exist yet and is not designed here.
- **Undo.** `3 d` can now destroy nine units of text in one keystroke with no way back. Nothing in the repo has
  undo. This ticket set makes that gap bigger without closing it.

## Out of scope

- **Search beyond the fence.** Search stays fenced, with span hardcoded to 1 in its own bound computation, as its
  own comment says. Widening search is a different decision.
- **Counts on `;` commands.** Command state compiles a sentence already; `3 <sentence>` is not a language change.
- **Session-scoped viewport state / simultaneous attach / multi-user editing** — unchanged, still the next effort's
  problem, still a prerequisite for anything per-client.
- **Mouse, marks, visual-block mode, registers, undo as a feature.**
