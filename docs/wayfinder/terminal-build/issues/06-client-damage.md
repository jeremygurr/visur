# Terminal client: damage model and the two paint paths

Type: task
Status: open
Blocked by: 05

## Work

Spec §5.3 components 4–6.

- A cell grid (`Vec<Cell>`, `Cell { ch, fg, bg, attrs }`) built from `rows`.
- Diff previous against next, producing **damage as row runs** — never rectangles.
- Two paint paths: `diff` and `full_repaint`; `full: true` selects the latter and is the only thing that discards cached rows.
- Escape emission: `CUP` per dirty row, maximal same-attribute runs, `SGR` only on change, `EL` for blank tails, cursor placement, and **one `write(2)` per frame** to fd 1.

**Measure both**, because it is not obvious diffing pays at 30–60 rows: record full-repaint time and diffed-paint time at 100×30 and 250×70. If diffing costs more than it saves, that is a finding for the conformance ticket — the spec's reason for it is headroom at large viewports, not principle.

## Done when

- A frame's bytes are demonstrably proportional to what changed, not to the screen, when one character is typed.
- Measured paint time stays inside the spec's budget in both paths at both viewport sizes.
- Cursor and span rendering are still correct after a partial repaint (the classic damage bug: a block cursor left behind on the old cell).
