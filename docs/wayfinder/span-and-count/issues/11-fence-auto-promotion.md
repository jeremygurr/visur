# Fence auto-promotion, transient

Type: task
Status: open
Blocked by: 02, 07

## Work

A fence too small to contain the selection is useless, so it grows to fit — **without ever rewriting what `/` set**.

Compute an *effective fence* for any operation that needs one:

1. Start from the current `scopeQuantum`.
2. While the extent (count × span units of the cursor quantum, from where the cursor actually is) does not fit
   inside one unit of the fence, promote the fence one rank up `character → word → line → document`.
3. `document` is the last rank: if the extent still does not fit, clamp at the end of the content and keep the
   requested span, per [ticket 03](03-overflow-order.md).

Measure against the cursor's real position, not the fence's worst case: five words starting at column 80 of a long
line may well fit inside that same line.

The ladder is sound by construction — `word` is `\S+`, so a word never straddles a newline and always sits inside a
line. Two consequences worth naming in the code:

- a `character` or `word` fence **cannot contain a multi-unit selection at all**, so any span or count above 1
  promotes immediately;
- promotion never writes `scopeQuantum`. The model's fence is the search fence, it is what `/` sets, and it is what
  the mode line reports ([ticket 10](10-mode-line-scope.md)). Silently widening it would turn `f` into a
  document-wide search because the user selected a lot of text, which is the failure this design exists to avoid.
  The effective fence is a local value passed to whoever asks for it.

Where the selection is concerned this is the same rule as [ticket 03](03-overflow-order.md)'s document-order counting,
seen from the fence's side; that ticket owns the sentence and this one implements only the ceiling that **operators**
hit. Use the rank ladder from [ticket 07](07-frame-at-span-one.md)'s structural comparison rather than a second
ad-hoc ordering.

## Done when

- Tests: fence `line`, word quantum, span 5 with 4 words left on the line → the extent reaches into the next line and
  the fence promoted to `document`; fence `line` with 5 words that fit → no promotion; fence `word`, span 2 →
  promotes to `line`; fence `document`, huge extent → clamps at content end with the requested span intact.
- `scopeQuantum` is unchanged by every one of those tests — asserted, not assumed.
- The mode line reports the fence `/` set, never the effective one.

## Notes

If it turns out that operators need the same effective fence the selection already computed, that is the seam telling
you these two are one value; pass it once rather than recomputing it twice and risking they disagree.
