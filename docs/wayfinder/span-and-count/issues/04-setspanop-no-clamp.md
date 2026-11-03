# `SetSpanOp` stops clamping; span survives `d` and `y`

Type: task
Status: open
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
