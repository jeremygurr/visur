# Fence auto-promotion, transient

Type: task
Status: closed
Closed: 2026-11-04
Blocked by: 02, 07

## Work

~~A fence too small to contain the selection is useless, so it grows to fit — **without ever rewriting what `/`
set**.~~

**Struck: the fence has no second job.** It is the fence a search looks inside. It is not a ceiling on `d`, `y`, `p` or
paste, so there is nothing to promote.

Compute an *effective fence* ~~for any operation that needs one~~ for no operation:

1. ~~Start from the current `scopeQuantum`.~~
2. ~~While the extent (count × span units of the cursor quantum, from where the cursor actually is) does not fit
   inside one unit of the fence, promote the fence one rank up `character → word → line → document`.~~
3. ~~`document` is the last rank: if the extent still does not fit, clamp at the end of the content and keep the
   requested span, per [ticket 03](03-overflow-order.md).~~ That clamp was already the only one, and it lives in the
   bound functions.

The defect in the draft is structural: **a fence that grows to hold the extent it is supposed to cap never caps
anything.** Promotion runs to `document` whenever the extent is wider than the fence, and at `document` the only
remaining limit is the end of the content — which [ticket 03](03-overflow-order.md) already established as the single
clamp on selection. So "the fence is the operators' ceiling" and "the fence grows to fit the operators' extent"
describe an unfenced operator wearing a fence's clothes, and [ticket 12](12-count-on-operators.md)'s "eight lines or
the fence's worth, whichever is smaller" has no second answer to give.

~~Measure against the cursor's real position, not the fence's worst case~~ — moot. What survives of the ladder is
where it was always needed: ranking which of two quanta is the frame for `[` / `]`, structurally at span 1
([ticket 07](07-frame-at-span-one.md)).

## Done when

- ~~Tests: fence `line`, word quantum, span 5 with 4 words left on the line → the extent reaches into the next line
  and the fence promoted to `document`~~ and the rest of that list, struck with the feature.
- `scopeQuantum` is unchanged by operators and by selection — asserted, not assumed. **Kept**, and now pinned.
- ~~The mode line reports the fence `/` set, never the effective one.~~ There is no effective one. Kept in the
  weaker form the mode line already satisfies: it reports the fence `/` set, full stop
  ([ticket 10](10-mode-line-scope.md)).

## Result

**No code changed.** The operator path never read `scopeQuantum`: the only readers in the server are
[`SearchForwardOp`](../../../../src/main/java/com/ple/visur/SearchForwardOp.java),
[`SearchBackwardOp`](../../../../src/main/java/com/ple/visur/SearchBackwardOp.java),
[`View`](../../../../src/main/java/com/ple/visur/View.java) for the mode line, and `ChangeScopeOp` — the `/` key —
which is the only writer. That is why the drafted second job was survivable to drop: it had never been wired up. Had
ticket 11 been built first and ticket 12 second, the contradiction would have surfaced as a fence that appeared to
limit `d` and did not, which is worse than either rule on its own.

### What replaced it

Four tests in [`SearchFenceTest`](../../../../src/test/java/com/ple/visur/SearchFenceTest.java), which is the first
test search has ever had:

- **The fence stops a search.** Two `needle`s, one per line, `line` fence: the first search lands on the first, the
  second leaves the cursor where it was. The next needle is out of bounds, so "found nothing" is the whole answer.
- **The control**: same content, same two searches, `document` fence → the second search reaches the second line.
  Without this pair the first test only proves search is broken somewhere.
- **A selection runs across a fence it is smaller than.** Five words from inside `alpha` with a `character` fence —
  the narrowest there is — ends at the end of `zeta`, one line down.
- **A delete across the fence goes across the fence**: `d` removes all five words and leaves the fence itself
  unmoved.

The last two are the rule in the user's words: *selection actions should not be impacted by the fence.* They are
green on the code as it stands, which is the point — they are guards, not a feature.

### Why this is worth the ticket rather than an edit

The fence now has one job, and the mode line reports it ([ticket 10](10-mode-line-scope.md)). A reader who sees
`SCOPE=LINE` next to a nine-word selection should not have to work out whether `3 d` stops at the line: it does not,
and one sentence in the map says so.
