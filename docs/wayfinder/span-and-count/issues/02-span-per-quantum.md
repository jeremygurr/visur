# Span > 1 in each quantum's bounds

Type: task
Status: closed
Closed: 2026-11-03
Blocked by: 01

> [!note] Most of this landed with [ticket 01](01-bound-seam-and-tests.md)
> The seam tests specified span rather than pinning the old clamp, so character, line and document already implement
> the rules below, with tests. What was left is the **word** quantum: its tests pinned two behaviors that looked wrong
> rather than confirming what is right, and both are named in the Result below.

## Work

Make the static bound functions from [ticket 01](01-bound-seam-and-tests.md) mean what their `span` parameter has
always claimed, per quantum:

- **character** — `[ca, ca + span]`, clamped only at the end of the content. It deliberately crosses line breaks:
  wrapping is a display concern, and the existing code already treats the last newline index as the content end
  rather than a wall. Today it returns `{ca, ca + 1}` and never looks at `span`.
- **word** (`RegexQuantum`) — already loops `span` times in `getBoundaries`, and its `move` already spends a span per
  quantum crossed. Verify with tests rather than trusting it: what must hold is that span 2 covers *both* words and
  the whitespace between them as one range, and that two words at the end of a line behave the same as two words in
  the middle of one. Fix only what the tests show is wrong.
- **line** — `bounds[1]` is the end of the `(cy + span - 1)`th line, not of `cy`. Note the existing end-of-content
  special case (the last index may be a phantom line) and keep it working at span > 1.
- **document** — whole document at any span ≥ 1. It does **not** clamp to 1, and it does not error above 1: with one
  document, "this document and the next two" is the same selection as "this document". The number stays legal because
  documents are expected to multiply.

## Done when

- New tests, one per quantum, asserting the exact range at span 1, 2, 3 from a known cursor position — including the
  case where the requested span runs off the end of the content.
- Span 0 still means "no selection": every quantum returns `{ca, ca}`.
- The word-quantum tests are written from the spec above, not from what the current loop happens to do, and any place
  those disagree is reported here before it is changed.

## Notes

The selection is `[start, end)` — inclusive start, exclusive end — which is what both renderers already assume
([`is_selected`](../../../../terminal/src/screen.rs), [`drawSpan`](../../../../src/main/resources/static/visur.js)). Keep
it; do not invent an inclusive end.

## Result

One rule covers all three symptoms: **a word span covers whole words and the whitespace between them, and a span
starts only where a word actually is.**

- **No word under the cursor selects nothing.** `"one two"` at the space gives `{3, 3}`, an indent in `"  one"` gives
  `{0, 0}`, and `"one   two"` from inside the wide gap gives `{4, 4}` — the answer a blank line already gave, which is
  what a cursor between two words should give too. The number survives; only `s` writes that, so moving back onto a
  word selects it again at the span the user asked for.
- **A span stops at the end of the last word** rather than taking trailing whitespace: `"one two   "` at span 3 is
  `{0, 7}`, not `{0, 10}`. Trailing whitespace is not *between* two words.
- **The walk between words crosses the gap in one step.** `"one   two   three"` at span 2 is `{0, 9}`; before, it was
  `{0, 4}` — one word and one character of space, because `getNextBound` called on a non-match returned `bound + 1`.
  Invisible across a single space, which is every fixture written before this, and wrong across every wider gap.

The two pinned behaviors named above are replaced by tests of the rule; the fix is in
[`RegexQuantum`](../../../../src/main/java/com/ple/visur/RegexQuantum.java), where the duplicated match-walk inside
`getNextBound` also became the `quantumEnd` call it had been reimplementing.

### Why "nothing" rather than "the next word"

The other candidate read the cursor's position as "the quantum after me", vim's `w`. It was rejected because a blank
line already answers *nothing selected* — and that answer is load-bearing, pinned in
[`RelativeMoveOpTest`](../../../../src/test/java/com/ple/visur/RelativeMoveOpTest.java) as the reason the span survives
an empty line. Reaching for the next word would make an indent select across the rest of the file, and would mean two
kinds of whitespace behave differently.

### Movement changed too, in the same direction

`getNextBound` is shared with `move`, so word movement across a wide gap now lands on the next word instead of one
character at a time, and at the end of the document it stays on the last word rather than stepping past it. Nothing
tested the old landing spots: every existing movement fixture uses single spaces, where the two agree.

### Consequences for [ticket 07](07-frame-at-span-one.md), in both directions

Fixing the walk made its ranking example real: twenty words past a run of blank lines now out-measure the twenty lines
that stop at it, so [`QuantumFrameTest`](../../../../src/test/java/com/ple/visur/QuantumFrameTest.java) gained
`twentyWordsOutMeasureTwentyLinesAndTheLineStillFrames`, which fails if the ranking goes back to the live span.

And it removed the tie that ticket 07's own fixture measured: a word on a blank line now measures *nothing* rather than
one character per span, so `] w` from a blank line ranks twenty characters against nothing and frames by characters
whatever the span. Those two tests still pin the frame decision, but no longer the ranking — the ticket's Result says
so where it named the old mechanism.

### Not done

Span 0 *inside* a word still gives `{wordStart, ca}` — the range from the word's start to the cursor
(`zeroSpanInsideAWordReachesFromTheWordStartToTheCursor`). It looks like the same family of oddity, but nothing has
asked for it and it is a span-0 question, not a span semantics one. So the acceptance line "span 0 means every quantum
returns `{ca, ca}`" holds for the character, line and document quanta, and for a word quantum whose cursor is not
mid-word.
