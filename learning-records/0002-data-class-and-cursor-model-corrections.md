# Lessons 7–8 corrected the itinerary's model of the data-class and display systems

Three claims in [`lessons/itinerary.md`](../lessons/itinerary.md) are materially
wrong. Lessons 7 and 8 replace them with source-grounded models, and future
lessons must not re-introduce the old framing.

1. **Two parallel trees, not a five-level ladder.** The type system is schema
   (`DataClass` → `PrimitiveDataClass` / `OuterDataClass` → `CompoundDataClass` /
   `LayeredDataClass`) beside instances (the matching `*Brick` classes).
   `DataForm` is an orthogonal value wrapper, not a level. See
   [`src/main/java/DataClass/`](../src/main/java/DataClass/) and
   [`reference/data-class-map.html`](../reference/data-class-map.html).
2. **The cursor's conversion math is on the layer, not the sub-compounds.**
   `CAAndNLDC` and `CXCYAndNLDC` are containers; both directions live in
   `CoordinatesDC.calcInternal`, computed from a shared read-only `nl` brick. See
   [`src/main/java/CursorPositionDC/CoordinatesDC.java`](../src/main/java/CursorPositionDC/CoordinatesDC.java)
   and [`reference/cursor-coordinates-map.html`](../reference/cursor-coordinates-map.html).
3. **The "display subsystem" is mostly stubs.** `Coordinate`, `ViewCell`, and
   `VisurColor` are unused; `LineWrapping` is a two-value enum nothing branches
   on. Real wrapping lives in `canvasWidth` + `RelativeLineBoundCalculator` +
   the scroll methods in `EditorModelCoupler`. Only `MovementVector` from the
   itinerary's display list is real. See
   [`src/main/java/com/ple/visur/`](../src/main/java/com/ple/visur/).

**Status:** active.

**Evidence:** source review behind
[`lessons/0007-the-data-class-hierarchy.html`](../lessons/0007-the-data-class-hierarchy.html)
and
[`lessons/0008-cursor-position-coordinates-and-display.html`](../lessons/0008-cursor-position-coordinates-and-display.html).
Not yet verified by learner exercises.

**Implications:** trust the source over the itinerary. Every itinerary bullet
names a type, so before teaching it, confirm the type exists and is actually
used. The "itinerary says vs. what the code does" pattern has been the
highest-value part of lessons 7 and 8 — keep it where a claim is wrong, but do
not manufacture corrections where the itinerary is accurate.
