# Overflow counts units in document order

Type: task
Status: open
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
- One function walks units, called by all four quanta — not four copies of the loop.
