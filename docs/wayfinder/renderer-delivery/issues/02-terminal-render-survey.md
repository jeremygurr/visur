# 02 — Survey of incremental rendering and terminal capability baselines

Type: research
Status: open

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
