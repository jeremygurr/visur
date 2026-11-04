# Overflow counts units in document order

Type: task
Status: closed
Closed: 2026-11-03
Blocked by: 02

## Work

A span of five words with four words left does not stop at four. Units keep counting in document order — straight
across line breaks — and only the end of the content stops them.

Implement this once, in the shared helper [ticket 01](01-bound-seam-and-tests.md) created, as
"walk N units forward from here, wherever they are", and have every quantum use it rather than each clamping at its
own boundary. Today [`LineQuantum`](../../../../src/main/java/com/ple/visur/LineQuantum.java) can only ever describe the
current line, and that is the clipping this ticket removes for the *selection* (the fence keeps its own bounds for
search, which is a different question and lives in [ticket 11](11-fence-auto-promotion.md)).

**One rule, stated once.** The design session settled this two ways and they are the same rule: "span overflow
continues in document order" and "the fence auto-promotes until the span fits" describe one behavior from either
side. This ticket owns the sentence; [ticket 11](11-fence-auto-promotion.md) references it and implements only the
*operator ceiling*, which is where the fence is a limit rather than a container.

Keep the requested number when the content runs out: asking for 50 words in a 12-word document selects 12 and stores
50, so moving back into the middle of the document restores a 50-unit window. This is deliberate — see the decision
in [the map](../map.md) about documents multiplying.

## Done when

- Tests: span larger than the document; span crossing one line break; span crossing several; span starting inside the
  last word; span requested beyond the end of content and then movement back left, showing the window returns to full
  size.
- The stored span equals the requested span in every case, including the impossible ones.
- ~~One function walks units, called by all four quanta — not four copies of the loop.~~ Struck; see the Result.
## Result

**Already true, with one exception — and the exception was a movement bug, not an overflow one.**

Each quantum states the range in the units it already has:

| quantum | how the far end is found |
| --- | --- |
| character | `Math.min(ca + span, contentEnd)` |
| line | `lineEnd(nl, Math.min(cy + span - 1, nl.size() - 1))` |
| word | walks the pattern forward, one quantum at a time ([ticket 02](02-span-per-quantum.md)) |
| document | the whole content, at any span |

Only the word quantum walks, so the acceptance line "one function walks units, called by all four quanta" is struck: it
would be a walker called from one of four places, where the other three read their answer out of an index. What this
ticket blamed `LineQuantum` for — "can only ever describe the current line" — was the pre-seam implementation, which
[ticket 01](01-bound-seam-and-tests.md) replaced with the `Math.min` above. The clamp that is there is the end of the
content, which is the only thing the ticket wants to clamp at.

### The round trip is now pinned rather than assumed

That the *stored* span is not clamped comes from [ticket 04](04-setspanop-no-clamp.md), but nothing showed the window
growing back. `SetSpanOpTest.aWindowThatRanOutOfContentGrowsBackToTheSizeThatWasAskedFor` asks for five words at the
last word of a twenty-five word line, then walks back: one word selected, then 2, 3, 4, 5, then holding at five. A span
clamped to what was available at the time would leave that at one word forever.

Two more at the seam, from the ticket's own list: five words across four line breaks (`WordQuantumBoundsTest.aSpanOfWordsRunsAcrossSeveralLineBreaks`),
and a span that starts inside the last word (`aSpanStartingInsideTheLastWordStopsAtTheEndOfTheContent`) — which starts
at the word's start as it does at span 1, and stops at the end of the content.

### What fell out of the round trip

The word quantum was reading the span as a *distance* in `move`: with five words selected, `h` went back five words.
[Ticket 06](06-count-on-jumps-and-movement.md) settled the opposite — the count says how far, the span says how wide —
but settled it through presses at span 1, where the two rules agree, so the word quantum kept the old reading. Fixed in
its own commit, with the span-0 half of the same branch pinned too: an unselected word move had been stopping at the end
of the word under the cursor while a selected one went to the start of the next, two answers for one key.

Nothing else in the four quanta treats the span as a distance: `CharacterQuantum.move`, `LineQuantum.move` and
`DocumentQuantum.move` read it as a condition (`span > 0`) at most.
