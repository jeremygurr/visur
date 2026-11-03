# `SetSpanOp` stops clamping; span survives `d` and `y`

Type: task
Status: closed
Closed: 2026-11-03
Blocked by: 02

## Work

Delete the clamp:

```java
spanToSet = spanToSet > 0 ? 1 : 0;   // SetSpanOp.java:9 — remove this line
```

Then make the rest of the operator agree with a span that can be 12:

- Set the requested number, not a boolean. `spanToSet == 0` still means collapse, and the bounds are recomputed at
  the new span exactly as they are now.
- Span goes to 0 **only** when there is genuinely nothing to select (`bounds[0] == bounds[1]`, which after
  [ticket 01](01-bound-seam-and-tests.md) is the one place that rule lives).
- `y` and `d` keep the count. [`YankSpanOp`](../../../../src/main/java/com/ple/visur/YankSpanOp.java) only reads the
  range, so it is already correct once the range is; [`DeleteCursorQuantumOp`](../../../../src/main/java/com/ple/visur/DeleteCursorQuantumOp.java)
  already recomputes bounds at the same span and needs verifying, not rewriting.
- `i`, `a` and `o` stash the span in `tempSpan` and restore it on `Escape`
  ([InitializerService](../../../../src/main/java/com/ple/visur/InitializerService.java)) — that mechanism is
  size-agnostic and must keep working with a span of 12.

Replace the `System.out.println` with nothing or a real log line; do not leave a debug print as the only record of a
state change.

## Amendments this ticket carries

- [renderer-delivery/map.md](../../renderer-delivery/map.md) — the "Span is binary" out-of-scope bullet is no
  longer true. Remove it and note the closure, linking here.
- [terminal-build/map.md](../../terminal-build/map.md) — same gap listed as "span clamped to 0/1". Same edit.
- [docs/reference/keys.md](../../../reference/keys.md) — update with [ticket 05](05-count-prefix.md) when the input
  form changes; update the span row here if the two land apart.

## Done when

- A test asserts that setting span to 7 leaves 7 in the model, and that setting it to 0 collapses the range.
- Selecting 3, deleting, and moving the cursor shows a 3-unit window again rather than 1.
- Neither map still claims the clamp exists.

## Result

Landed before [02](02-span-per-quantum.md) closed, since what it was waiting on was the quantum arithmetic, which came
with [01](01-bound-seam-and-tests.md).

The clamp is gone and the operator stores the number it was handed
([`SetSpanOp`](../../../../src/main/java/com/ple/visur/SetSpanOp.java)); the range still clamps at the content end,
but the model keeps the requested span rather than the width that survived
([`aSpanWiderThanTheContentKeepsTheRequestedSpanAndSelectsWhatIsLeft`](../../../../src/test/java/com/ple/visur/SetSpanOpTest.java)
names it). The debug print left with the clamp.

Seven tests in [`SetSpanOpTest`](../../../../src/test/java/com/ple/visur/SetSpanOpTest.java), booted the way a session
boots (`ServiceHolder.init` + `initializeEditorModel`) because the assertion is about what the model is left holding.
Five of the seven fail against the old clamp, so they test the ticket rather than the operator's shape.

Two of the ticket's bullets needed no code, and are now covered by tests instead of by argument:

- `d` keeps the count because it never wrote span — it recomputes bounds at the span already in the model, and
  [`SpanCollapse`](../../../../src/main/java/com/ple/visur/SpanCollapse.java) is the only thing that can zero it.
  `y` reads the range and writes nothing.
- The `tempSpan` stash is [`AssignmentOp`](../../../../src/main/java/com/ple/visur/AssignmentOp.java), which copies
  `getVal()` — an `Integer` — with no view of its magnitude.

**What this makes reachable from the keyboard**: `s 3` selects three characters, `s 5` five. The digit-after-`s`
submode was already passing any digit to `setSpan`; the clamp was the only thing refusing. Nine is the ceiling until
[ticket 05](05-count-prefix.md) replaces the form, along with two behaviors that ticket decides:
**bare `s` still does nothing** (the submode leaves itself on a non-digit without calling `setSpan`, so it does not
collapse to 1 as decided) and **`s 0` collapses**, which matches the decision.
