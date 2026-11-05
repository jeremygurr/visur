# Selections include their separator; `\` toggles it; the highlight shows it

Type: task
Status: closed
Blocked by: 01

## Work

A selection now covers **the unit plus whatever separates it from the next unit**, by default, and the highlight draws
it — so a line selection shows one extra cell at the end of the row standing for the newline, and `d` on it leaves no
stray blank line.

**The rule.** After a quantum's own bounds are computed, the far end is extended over the **run** of that quantum's
separator characters, if one starts there:

| quantum | separator | `1 s` on `"one two three"` | at a line's end |
|---|---|---|---|
| character | none | `"o"` | `"o"` |
| word | space, tab | `"one "` | last word only, no newline |
| line | one newline | `"one two three\n"` | (is the terminator) |
| document | none | everything | — |

"Run" means the whole gap: `2 s` on `"one   two   three"` is `"one   two   "`. Word separators are **space and tab,
never newline** — a newline ends the line, it does not separate two words — so a word selection at the end of a line
takes nothing. Newlines already inside a selection stay inside it.

**Where it lives.** `getBoundaries` is abstract in all four quantum classes today. Make it a `final` method on
[`Quantum`](../../../../src/main/java/com/ple/visur/Quantum.java) that calls the per-class work and then applies a pure
static `Separators.including(content, bounds, separators)`. The `boundsFor` statics from the previous map keep their
five arguments and their tests: the rule is applied *after* the per-class answer, by code with its own seam and its own
tests.

Two limits that are part of the rule:

- **It extends the far end once, not once per unit.** `3 s` on words is three words plus the one gap after the third.
- **It applies only when the range is non-empty.** At span 0 the range stays collapsed at the caret, so `p` still
  inserts where the bar cursor is and a blank line still answers "nothing selected"
  ([ticket 02 of the previous map](../../span-and-count/issues/02-span-per-quantum.md)).

**`\` toggles it.** A navigate-mode binding, **sticky for the session** — a preference about what a selection *means*
should not silently flip when a file loads. With `SEP=OFF` the bounds *and* the highlight shrink together; the whole
value of showing the newline cell is that the two states look different.

**Mode line.** `SEP=ON` / `SEP=OFF` goes **left of `SPAN=`**, giving quantum → fence → sep → span → count. The row
clips at the right (the order is the design, [ticket 10](../../span-and-count/issues/10-mode-line-scope.md)), so a
separator indicator to the right of the span would be the first thing lost — a toggle you cannot see is a toggle that
lies. Both renderers: `terminal/src/screen.rs` `mode_line()` and `drawModeLine()` in
[`visur.js`](../../../../src/main/resources/static/visur.js).

## Done when

- `Separators.including` is a pure static with per-case tests: whole run of spaces; a tab; **a newline after the last
  word is not taken**; a line takes its terminator; the last line of a document takes nothing; an empty range is left
  collapsed; the far end is extended once regardless of span.
- Bound tests at the quantum seam that used to assert `{0,3}` for one word now assert `{0,4}`, and the ones that pinned
  "trailing whitespace is not between two words" are **replaced rather than deleted** — same fixture, new expectation,
  so the reversal is visible in the diff.
- `LineQuantumBoundsTest.aLineEndingInANewlineEndsBeforeItWhenAnotherLineFollows` reverses. This is a deliberate
  reversal of a ticket 01 pin, and the test that replaces it says why in its name.
- `d` at the line quantum leaves **no** blank line: `"one\ntwo\n"` with line one selected deletes to `"two\n"`, and the
  register holds `"one\n"`.
- `y` then `p` at the line quantum round-trips the document byte for byte, including the final newline.
- `\` in both states, through keypresses: `SEP=ON` bounds include the separator, one press of `\` gives the old bounds,
  a second press restores. Span, cursor quantum and search are untouched by the toggle.
- Mode line asserts `SEP=ON` sits before `SPAN=`, and that it **survives the 40-column clip** — the existing
  clip test moves to whatever is now the first thing lost.
- `SearchFenceTest` still passes: the fence is a bound computation at span 1 and so grows by one cell at the line
  quantum. Search reaching one character further is expected; the tests assert the second line is still unreachable.
- [x] Manual (user-driven) check — covered: `s` at line quantum highlights past the end of the row, `\` stops it at the
  last visible character, and `d` then `p` round-trips with no blank line. Recorded here rather than as a sitting at the
  time: [03](03-paste-replaces-selection.md) through [06](06-undo-redo.md) were each checked by hand on top of these
  bounds, and the separator toggle was in the mode line for all of them.

## Notes

- **This ticket resolves the open question ticket 12 raised** ("deleting N lines leaves a newline behind… vim's `dd`
  takes the line whole"). It was never a count bug: it is the line quantum's end edge, and this is the edge moving.
- The reversal is the part to be careful with. Ticket 01's `aLineEndingInANewlineEndsBeforeItWhenAnotherLineFollows` and
  ticket 02's "trailing whitespace is not between two words" were both *deliberate* and both argued for. They are
  wrong now because the goal changed — from "select the words" to "select the unit and what joins it to the next" — so
  the commit message should say that rather than describing it as a fix.
- `p`'s insertion point does not move in this ticket even though the line case is now cleaner. Paste is
  [03](03-paste-replaces-selection.md).

## Result

Shipped across `49e32d9` (the pure `Separators.including` seam), `f5ecc33` (the toggle), and `dd5b03c` / `b7d2fcc` /
`e428b82` (mode line and both renderers' expectations). The rule lives after each quantum's own answer, so no quantum
class grew a separator argument, and `SEP=ON` / `SEP=OFF` sits between `SCOPE=` and `SPAN=` where the 40-column clip
reaches it.

Closed on the user's instruction after [06](06-undo-redo.md): the ticket had no `## Result` and no recorded manual check,
only shipped code and tests, which is the gap this section fills. `keys.md` gained the `\` row and a section for the
rule at the same time — until then the behavior was documented only as something "in flight".
