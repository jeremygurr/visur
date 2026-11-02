# Terminal client: damage model and the two paint paths

Type: task
Status: closed
Closed: 2026-11-01
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

## Result

Two pure functions in [`src/paint.rs`](../../../../terminal/src/paint.rs) — `diff(prev, next)` and
`frame(prev, next, full)` — and one cached grid in the loop. `full()` was already there; the diff is the new path.

### The damage

Row runs, never rectangles: for each row, the first and last differing cell, one `CUP`, the cells from there with `SGR`
only where the attribute changes, and one `EL` when the row got shorter — after resetting the attribute, or the erase
paints the tail in reverse video. A row that did not change emits nothing at all, and every frame ends with the cursor,
which is placed even when no cell changed (a cursor is never painted into a cell here, which is the whole reason the
classic "block left on the old cell" bug cannot happen — the test locks that in).

The run stops at the new row's end instead of writing blanks that are about to be erased, and `EL` is emitted only when
the row now ends at or before that point. The first version of that rule erased valid text whenever a change stopped
mid-row; the mode line caught it.

### Measured — both paths, both sizes

`cargo test --release --bin visur-term measure_both -- --ignored --nocapture`, M2 Max, release, median of 200:

| Viewport | Path | Bytes | Time |
|---|---|---|---|
| 100×30 | full repaint | 3,234 | 16.3 µs |
| 100×30 | diff, one character | **26** | **3.9 µs** |
| 100×30 | diff, every row changed (a scroll) | 3,119 | 8.0 µs |
| 250×70 | full repaint | 18,014 | 51.2 µs |
| 250×70 | diff, one character | **26** | **17.2 µs** |
| 250×70 | diff, every row changed | 17,749 | 24.5 µs |

**Verdict: diffing pays, at both sizes, in bytes and in time.** A keystroke is 124× fewer bytes at 100×30 and 693× at
250×70; even the worst case — every row changed — is faster than a full repaint, because a full repaint scans each row
for its painted width and then formats it. Both paths are three orders of magnitude inside the 16 ms frame budget, so
the reason to diff is not the paint time at 30 rows: it is the wire and the terminal, and the headroom at 250×70.

The one-character case is the *slowest* diff (17.2 µs at 250×70) because a small change means scanning the whole row
twice before writing three bytes; a scroll finds its first and last differing cell immediately. That is the shape of
the trade and it is still 0.1% of a frame.

### Live, through the real server

`VISUR_E2E=1 cargo test --test pty -- --test-threads=1`, three fresh servers, three runs, all green. Keystroke → paint
fell from **5.91 ms to 3.43–3.69 ms median**: same round trip, 3.2 KB less to write per keystroke.

### The lesson worth recording

`tests/pty.rs` reconstructed the screen by reading the **last frame** — correct only while every frame was a full
repaint, and wrong the moment the client sent damage. It is now a small replaying model: `CUP`, `EL`, text, applied in
order across every frame. The live-server check failed loudly the first time and the harness was the bug, not the
client. It also taught the test to stop trusting the shared document's mode: it presses `Esc` at attach and asserts a
mode line exists rather than which mode it names, because the editor model outlives a run.

### Not done

- `Cell` still carries only `ch` and `inverse`; `fg`/`bg`/`attrs` wait for `VisurColor` (the ticket names them, the
  spec does not have them yet).
- No sync-path measurement: `?2026` wrapping adds 16 bytes per frame, unmeasured because the harness answers both ways
  but neither is a real terminal.
