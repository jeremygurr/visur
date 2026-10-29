# 02 — Survey of incremental rendering and terminal capability baselines

Type: research
Status: closed
Closed: 2026-10-28

## Question

How do existing terminal editors and multiplexers turn a changed screen into a minimal set of escape sequences, and what can Visur assume about a terminal's capabilities?

Answer two halves.

**Half A — incremental rendering techniques.** Look at how real implementations do it, not blog summaries:

- **Neovim's `ui_compositor`** — the canonical damage-region implementation. What granularity does it track (cell? row? grid?), how does it decide what to flush, how does it merge overlapping regions?
- **Kilo** (the ~1k-line ANSI editor) and similar small editors — what is the *minimum* viable approach, and what breaks without a compositor?
- **tmux** — how it redraws a pane and when it chooses a full redraw over incremental.
- **Alacritty / kitty / WezTerm** — what rendering hints a terminal accepts, and which of them meaningfully reduce work.

Capture the **cell-grid diff → escape sequences** technique: hold the previous and next screen as cell arrays, emit only the runs that differ.

**Half B — terminal capability baseline on macOS.** What Visur may assume without feature detection, and what it must query:

- Cursor addressing (`CUP`), erase (`ED`/`EL`), character attributes (`SGR`)
- Alternate screen buffer (`1049`) and its effect on scrollback
- Synchronized output (`DEC 2026`) — does it eliminate flicker on a full repaint, and is it available in the terminals you'd actually use?
- Bracketed paste (`2004`), truecolor (`COLORTERM` / 24-bit SGR), mouse reporting
- Resize signalling (`SIGWINCH`), and cursor-position reporting (`DSR`/`CPR`) as a fallback coordinate query
- Whether a *full viewport repaint* per keystroke is actually acceptable on a modern terminal — the same question [Baseline measurements of the current update path](01-baseline-measurements.md) asks about canvas, asked about terminal

Findings land as `research/terminal-render-survey.md` under `docs/wayfinder/renderer-delivery/`, with links to primary sources — upstream source files, not tutorials. The answer on this ticket summarises and links it.

Blocked by nothing. Blocks [Decide the view-update protocol and where its seam sits](03-decide-view-protocol.md) (payload shape) and [Decide the terminal client's shape, transport, and parity](08-decide-terminal-shape.md) (capability baseline).

## Answer

Full survey with citations: [Survey of incremental rendering and terminal capability baselines](../research/terminal-render-survey.md).

**The technique.** Every implementation is the same loop: keep two viewport-sized cell grids (`onScreen`, `next`), diff
them, and emit only the runs that differ — `CUP` to the row, maximal same-attribute runs, `EL` for the blank tail,
`CUP` for the cursor, one `write(2)` per frame. The wins, in order of value: a per-row hash to skip untouched rows,
blank-run → `ESC[K`, SGR emitted only on *changes*, trailing-cell elision, and a separate scroll verb for bulk movement.
ncurses (`doupdate`) is the reference implementation and adds the key insight: the renderer must model the terminal's
*assumed* cursor and attribute state, not just the grid.

**Neovim's granularity is a run of cells within one row** — never a rectangle, never a single cell. `grid_line` carries
`(row, col_start, cells[], wrap)`; `grid_scroll` carries a rectangle plus a signed row/col delta; the cursor is its own
event. Overlapping windows merge *implicitly* because composition happens on one flattened grid before diffing, so there
is no rect-intersection algorithm at all — compose first, diff second, coalesce adjacent dirty rows on flush.

**The minimum viable approach is a full viewport repaint** (Kilo: alt-screen, `ESC[H`, row text + `ESC[K`, cursor, one
write, per keystroke). It is bounded by the viewport rather than the document, which is exactly the property Visur
lacks — and what breaks without more is specific and cheap to fix: tearing on fast GPUs (`?2026`), cursor walk (`?25l`),
scrollback pollution (`?1049`), end-of-line residue (`ESC[K`), attribute bleed (`ESC[0m`), and unconditional
O(viewport) bytes (row hashes).

**tmux is the useful counter-example**: it diffs per row while the coordinate space is stable, and throws the diff away
whenever the coordinate space changes — resize, attach/detach, layout or pane change, alt-screen enter/exit, style or
terminal-feature change, explicit refresh. The principle to carry: *damage is only incremental while the coordinate
space is stable*, so a renderer needs exactly two paint paths, `diff` and `fullRepaint`, plus a small force-repaint event
set. Terminals themselves accept **no** damage hint — `?2026` (the only hint that matters) changes *perceived* quality,
not byte count, and nothing else reduces work except scroll/insert-delete-line escapes.

**Capability baseline.** Assume the VT100/ANSI core (`CUP`, `ED`, `EL`, `SGR`, `?25`), the alternate screen (`?1049`,
which also removes the terminal's scrollback from the design — so document scrolling must be a server-side viewport
re-render), bracketed paste (`?2004`), and `SIGWINCH` + `TIOCGWINSZ`. **Probe** `?2026` (via `DECRQM`, `CSI ? 2026 $ p`)
and truecolour (`COLORTERM`, terminfo `Tc`) once at startup with a short timeout. Terminal.app must be assumed to lack
`?2026`, and 24-bit colour only arrived with macOS Tahoe.

**Verdict on full repaint per keystroke: acceptable.** It is viewport-bounded and, at ≤200×60, low single-digit KB with
SGR diffing — well inside a frame locally. What is *not* acceptable is a network round-trip per keystroke, and that is
where Visur's real cost sits today: `modelWasChanged` ships the whole buffer, so payload scales with the document while
paint does not. That predicts what [Baseline measurements of the current update path](01-baseline-measurements.md) will find and is consistent with
the map's "measure before optimizing" stance.

**What this implies downstream.** For [Decide the view-update protocol and where its seam sits](03-decide-view-protocol.md):
model the payload on Neovim's vocabulary — a viewport slice of cells with an explicit change kind (`full`, dirty row
runs, and a distinct `text-inserted` for bracketed paste), semantic colour that the renderer downgrades, and damage
expressed as row runs so rectangles never enter the design. Bracketed paste forces a separate message type or paste
degenerates into one round-trip per character. For [Decide the terminal client's shape, transport, and parity](08-decide-terminal-shape.md):
the two paint paths, a single `write(2)` per frame, a kqueue-based raw-mode read/signal loop, and startup probing of
`?2026` + truecolour are the whole renderer core — a few hundred lines, language-agnostic, which is consistent with the
settled out-of-JVM decision.

Residual risk: this run had search but no page-fetch, so upstream source claims are architecture-level with the exact
files cited but not opened; `grid_line`'s trailing-cell elision rule and tmux's exact redraw-trigger list should be
confirmed against the source before being encoded in the spec.
