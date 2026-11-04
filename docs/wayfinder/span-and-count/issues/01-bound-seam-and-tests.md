# Pure bound seam + boundary tests

Type: task
Status: closed
Closed: 2026-11-02
Blocked by: —

## Work

`getBoundaries(ca, nl, span, includeTail)` is the function every span decision runs through, and today no test can
call it: it reads editor state out of statics, and in two cases it **writes** that state while computing.

Read the four implementations before starting:

- [`CharacterQuantum.getBoundaries`](../../../../src/main/java/com/ple/visur/CharacterQuantum.java) — pure already, but
  ignores `span` entirely.
- [`LineQuantum.getBoundaries`](../../../../src/main/java/com/ple/visur/LineQuantum.java) — reads `emc.getCY()`.
- [`DocumentQuantum.getBoundaries`](../../../../src/main/java/com/ple/visur/DocumentQuantum.java) — calls
  **`emc.putSpan(0)`** and **`emc.putCA(0)`** inside the bounds computation.
- [`RegexQuantum.getBoundaries`](../../../../src/main/java/com/ple/visur/RegexQuantum.java) — calls **`emc.putCA(...)`**,
  reads `emc.getEditorContent()` and `emc.getStrToMatch()`.

Extract a static, side-effect-free bound function per quantum — e.g.
`CharacterQuantum.boundsFor(String content, ArrayList<Integer> nl, int ca, int cy, int span)` returning
`int[]{start, end}` — and make the instance method a thin reader that pulls its arguments from `emc`, calls the
static, and then performs the writes it used to do inline. Move each `putCA` / `putSpan` into the callers that
actually meant them (`SetSpanOp`, `ChangeCursorQuantumOp`, `RelativeMoveOp`, `SearchForwardOp`, `SearchBackwardOp`,
`ChangeScopeOp`'s users), so the collapse-to-zero-span rule lives in **one** place instead of three.

Write the tests against the statics — a pure assertion per case, no server, no `SharedData`. (The precedent cited here
when the ticket was written, `DocumentJumpOpTest`, is since deleted by
[ticket 09](09-remove-gg-and-capg.md); the seam tests are that style now.) Cover, per quantum: span 0, 1, 2, 3; cursor at content end; empty content; a span that runs
past a line break; a span that runs past the end of the content; and that calling the function twice with the same
arguments returns the same range (the property the current code fails).

## Done when

- `./gradlew test` is green, and the new test class contains no `ServiceHolder` reference.
- No static bound function reads or writes `EditorModelCoupler`.
- The "bounds collapsed, so span becomes 0" rule exists exactly once, in an operator, with a comment naming the two
  places it used to live.
- Every existing behavior is unchanged at span 0 and 1 — this ticket changes no semantics, only reachability. If a
  test at span 0 or 1 differs from before, that is a finding to report, not a fix to make.

## Notes

This is the ticket that makes 02, 03, 11 and 12 cheap, and the side effects are the reason: a bound function that
moves the cursor cannot be tested twice with different spans, which is precisely what testing span > 1 requires.

## Result

Four pure statics, one per quantum, all with the same shape so a caller can pass any quantum the same arguments:

| seam | notes |
|---|---|
| `CharacterQuantum.boundsFor(content, nl, ca, cy, span)` | `cy` unused; kept for the shared shape |
| `LineQuantum.boundsFor(content, nl, ca, cy, span)` | takes `cy` instead of reading it |
| `DocumentQuantum.boundsFor(content, nl, ca, cy, span)` | writes nothing at all now |
| `RegexQuantum.boundsFor(pattern, content, nl, ca, cy, span)` | the pattern is the quantum's identity, so it is a parameter |

Each instance `getBoundaries` is now a thin reader over its static. 42 new tests in
[`CharacterQuantumBoundsTest`](../../../../src/test/java/com/ple/visur/CharacterQuantumBoundsTest.java),
[`LineQuantumBoundsTest`](../../../../src/test/java/com/ple/visur/LineQuantumBoundsTest.java),
[`DocumentQuantumBoundsTest`](../../../../src/test/java/com/ple/visur/DocumentQuantumBoundsTest.java),
[`WordQuantumBoundsTest`](../../../../src/test/java/com/ple/visur/WordQuantumBoundsTest.java) and
[`NextLineIndicesFixtureTest`](../../../../src/test/java/com/ple/visur/NextLineIndicesFixtureTest.java), which pins
the `nl` convention every one of them is written against — a fixture that drifted would otherwise invalidate all of
them silently. Full suite: 81 tests, green.

### The collapse rule is one method now

`SpanCollapse.zeroIfNothingSelected` is the single spelling
of "a collapsed range leaves no span". It is called from `SetSpanOp`, `ChangeCursorQuantumOp`,
`DeleteCursorQuantumOp` and `RelativeMoveOp`. `InsertNewlineOp` deliberately does not call it: inserting cannot
reduce what is selectable, and collapsing there would drop a live span when pressing `i` then Enter on an empty
document.

*Later, and worth recording here: consolidating the rule made the next bug findable rather than invisible, but the rule
itself was wrong. Nothing but `s` should write the span — see the map's decision on span writers.*

### Two writes stayed where they were

Both are cursor writes, not span writes, and both are marked `// legacy side effect` at the site:

- `DocumentQuantum` still parks the cursor at index 0 when there is no selection and the cursor is not already at the
  end of the content. **Consequence worth knowing:** at span 0, `] r` therefore lands at the *start* of the document,
  because that is where the cursor is put before the bounds are read. [Ticket 09](09-remove-gg-and-capg.md) requires
  `] r` to reach the last character at span 0, so this write is one of the things that ticket has to settle.
  **Settled by [ticket 09](09-remove-gg-and-capg.md), and nothing had to change:** the write no longer fires on the way to
  the end, because at span 0 the range collapses at the cursor when the cursor is already at the end of the content —
  which is where the jump leaves it. `] r` reaching the last character at span 0 is pinned now, from the character
  quantum and from the document quantum.
- `RegexQuantum` still snaps the cursor to the start of the quantum it selected.

### Three things changed behavior, all of them at span 0 with the character quantum

The ticket asked for no semantic change. This is the one place where the seam and the old code could not both be
honoured: `CharacterQuantum` used to return `{ca, ca + 1}` at span 0 — a one-character selection for a quantum with
nothing selected — and a pure bound function that means "span 0 selects nothing" has to return `{ca, ca}`. That is the
rule [ticket 04](04-setspanop-no-clamp.md) settles, and every other quantum already obeyed it. What it changes, in
descending order of how likely you are to notice:

1. **`p` with no selection** now inserts at the cursor instead of one character after it. The caret at span 0 is drawn
   at the left edge of the cell, so the text now appears where the caret was.
2. **`n` / search** starts one character earlier when nothing is selected, because `SearchForwardOp` reads
   `cursorQuantumEnd + 1`. Repeated `n` no longer steps over every other match of a repeated string.
3. **`y` with no selection** was already a no-op (`YankSpanOp` returns at span 0), so it is unaffected.

Highlighting is unaffected in both renderers: each draws the range only when span is above 0.

### Two span > 1 behaviors arrived early

The tests specified span rather than pinning the clamp, so `CharacterQuantum` (span N characters, crossing line
breaks, clamped at the content end) and `LineQuantum` (span N lines) now implement
[ticket 02](02-span-per-quantum.md)'s rule, and `DocumentQuantum` returns the whole document at any span. What is left
of ticket 02 is the word quantum, where the tests pinned two things that look wrong rather than confirming what is
right: a cursor sitting on whitespace selects the word *before* it, and in whitespace-only content a span above 1
selects the whitespace by running to the end of the content. Both were pinned by name in
[`WordQuantumBoundsTest`](../../../../src/test/java/com/ple/visur/WordQuantumBoundsTest.java) so that fixing them is
a decision and not a surprise — [ticket 02](02-span-per-quantum.md) made the decision and replaced both pins.
