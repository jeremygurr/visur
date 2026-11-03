# Terminal block cursor at `span > 0`

Type: task
Status: closed
Closed: 2026-11-03
Blocked by: —

## Work

The terminal decides cursor shape with a value that used to be the only nonzero one:

```rust
grid.cursor_block = view.span == 1;   // screen.rs:114 — becomes view.span > 0
```

At `span = 4` the terminal would highlight four cells and draw a **bar**, because the test was written when 1 was the
largest legal value. The canvas already gets this right — [`drawCaret`](../../../../src/main/resources/static/visur.js)
draws the thin bar when `span == 0` and stands in when the selection covers no visible cell — so this ticket brings
the terminal to parity rather than inventing a rule.

Also update the doc comment on `Grid.cursor_block` ("A one-cell span is drawn as a block, a zero-cell span as a bar")
and the test named for the old rule in [`paint.rs`](../../../../terminal/src/paint.rs)
(`a_zero_span_asks_for_a_bar_cursor_and_a_one_span_for_a_block`): assert bar at 0, block at 1, **block at 2**.

## Amendment this ticket carries

- [conformance.md](../../terminal-build/conformance.md) §5.5 row reads "Block cursor for
  `span == 1`, bar for `span == 0`" and is marked *met*. Once 2 is legal that statement is stale whether or not the
  code changes: rewrite it as "`span > 0` block, `span == 0` bar" with a note pointing here.

## Done when

- `cargo test` green in `terminal/`, including the new span-2 case.
- Manually (user-driven): a 3-word selection shows a block cursor at its start and inverse video across the range; a
  selection past the last character of the document still shows the bar.
- The conformance row describes the rule the code implements.

## Notes

No protocol change. `span` is already an `i64` on the wire
([`protocol.rs`](../../../../terminal/src/protocol.rs)) and both renderers already treat the highlight as a range; this
is the last place where a nonzero span is assumed to be exactly one.

## Result

`view.span > 0` in [`screen.rs`](../../../../terminal/src/screen.rs), and the two tests that pinned the old rule now
assert bar at 0, block at 1, block at 2 or 3. `cargo test` in `terminal/`: 45 unit + 10 pty, green; both new
assertions fail against `== 1`, so they test the rule rather than the shape.

Landed with [ticket 04](04-setspanop-no-clamp.md) rather than after it: that ticket made `s 3` reachable, and shipping
it alone would have shipped a client that highlights three cells and draws a bar.

**The amendment was bigger than the ticket listed.** It named the conformance row; the same claim sits in four places
in [the spec](../../renderer-delivery/spec.md) — the wire comment ("0 or 1 today"), §5.5's cursor-style rule, the
terminal client's Must list, and the feature-gap list. All four now read "any span above 0", because a spec that
contradicts the code it governs stops being the source of truth the terminal-build map points at.
