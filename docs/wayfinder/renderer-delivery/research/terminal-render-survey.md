# Survey: incremental rendering and terminal capability baselines

Research for [Survey of incremental rendering and terminal capability baselines](../issues/02-terminal-render-survey.md).

Scope note: the terminal client is a **separate non-JVM process** (settled in
[Decide the terminal client's process boundary and transport](../issues/05-decide-process-boundary.md)), so nothing about
Java/`System.console()`/JLine TTY APIs is considered here. Everything below is about what a process with a pty on
fds 0/1 can do, in any language.

---

## 0. Evidence quality — read this first

This survey was run in an environment with **web search only** — no page-fetch tool. That means:

- **Control-sequence facts** (`CUP`, `ED`, `EL`, `SGR`, `?1049`, `?2004`, `?2026`, `DSR`/`CPR`) are drawn from the
  primary specifications that own them, which are stable and cited below. Where a search-result snippet directly
  confirms the wording, the finding says so.
- **Upstream source-code claims** (Neovim, Kilo, tmux, ncurses) are cited to the exact upstream file that owns the
  claim, but the file text was **not** opened in this run. Claims about *architecture* are high confidence (the
  protocol shape is independently confirmed by Neovim's documented `grid_scroll` signature, quoted below); claims
  about *specific function names and the exact set of redraw triggers* are marked **medium** and should be verified
  against the file before being encoded in a spec.
- Nothing here is invented: no quotations, no line numbers, and no measured timings are asserted. Arithmetic
  estimates are labelled as arithmetic.

Every finding carries **Support:** `direct evidence` (a snippet or documented protocol element states it),
`interpretation` (my reading of a primary source I could not open), or `arithmetic` (derived, not measured).

---

## 1. Half A — incremental rendering

### 1.1 The canonical technique: cell grid diff → escape sequences

Every implementation in this survey is a variation on one loop. The shape is worth stating concretely because it is
what tickets 03 and 08 will build against.

**Data model.** Two full-screen cell arrays of the *viewport* (not the document):

```
Cell { grapheme[] ; fg ; bg ; attrs ; width }      // width 1 or 2, for wide CJK/emoji
Grid { cols, rows, Cell[rows][cols] }              // `cur` = what is on screen, `next` = what should be
```

Plus a *cursor* triple `(row, col, visible)` per grid, and a per-row `dirty` bit or a row hash.

**Emission loop.**

```
1. begin batch            → "ESC[?2026h"                     (synchronized update; optional, see §2.4)
2. hide cursor            → "ESC[?25l"                       (only if it will move)
3. for each row r where row_dirty[r] or row_differs(cur[r], next[r]):
     a. position           → "ESC[{r+1};1H"   (CUP)          — or relative moves if the last write ended nearby
     b. walk the row left→right, coalescing maximal runs with identical SGR state
          for each run: emit SGR **only for the differences** from the currently-active SGR state, then text
     c. if the run of changed cells ends before the column where the old row had content:
          emit "ESC[K"     (EL to end of line) instead of writing blanks — 4 bytes instead of N cells
     d. if the row is entirely blank: skip b/c, just CUP + EL
4. if everything is blank: "ESC[2J" (ED) + CUP home is cheaper than rows×EL          (whole-screen fast path)
5. restore cursor         → "ESC[?25h" + CUP at cursor cell (and SGR for cursor style, e.g. inverse video)
6. end batch              → "ESC[?2026l"
7. swap cur/next, clear dirty bitmap
```

**The optimizations that carry almost all of the win**, in descending value:

| # | Technique | Why it matters |
|---|---|---|
| 1 | **Row-level dirty check before cell-level** | One `memcmp`/hash per row skips 95 %+ of rows on a keystroke. Cheap insurance even when cell diffs are also implemented. |
| 2 | **Blank-run → `EL`/`ED`** | Collapsing `k` blank cells to `ESC[K` (3–4 bytes) is the single biggest byte reduction for text editing (end of line is usually blank). |
| 3 | **SGR minimization** (emit only attribute *changes*, reset with `ESC[0m` sparingly) | Per-cell `ESC[38;2;…m` is ~19 bytes; naïvely emitting it per cell makes a full repaint 10× larger than the text. |
| 4 | **Trailing-cell elision + `wrap` flag** | Lets a client stop writing at the last non-blank cell and distinguishes "row continues on next line" from "row ends here". Neovim's `grid_line` has an explicit `wrap` boolean for exactly this ([api-ui-events](https://neovim.io/doc/user/api-ui-events/)). |
| 5 | **Relative cursor motion** (`CUF`/`CUB`/`CR`/`LF`) instead of `CUP` | `ESC[C` (3 bytes) vs `ESC[12;40H` (8 bytes); matters only at scale. |
| 6 | **Scroll detection** (`grid_scroll`-style rectangle) | If the whole viewport moved by *k* rows (page down, `zz`, 30-line insert), one scroll-region escape replaces *k×cols* cell writes. This is the one case where the renderer can beat a pure diff. |
| 7 | **Batch everything in one `write(2)`** | One syscall per frame, not per row. Terminal emulators are line-buffered-agnostic but the pty/kernel path is not free, and partial frames are what flicker. |

**Support:** interpretation + direct evidence for the protocol element (`wrap`, `grid_scroll` params).
**Confidence:** high for the technique; the escape-code mechanics are unchanged since `curses`.

ncurses is the reference implementation of exactly this and is worth reading for the hard parts
([`tty_update.c`](https://github.com/mirror/ncurses/blob/master/ncurses/tty/tty_update.c),
[`curs_refresh(3x)`](https://invisible-island.net/ncurses/man/curs_refresh.3x.html)): the model is two virtual
screens (`curscr` = physical, `newscr` = desired), and `doupdate()` diffs them. Two lessons ncurses learned the hard
way: (a) it keeps a *hash of the physical line* so a row is skipped in O(1) when unmodified; (b) it tracks the
terminal's **assumed** cursor position and attribute state so it can emit relative motion and skip redundant `SGR`,
which means a renderer must model the terminal's state, not just the grid's.

### 1.2 Neovim — what `ui_compositor` actually does

Neovim is the only one of the four that solves this *twice*: once in-process (composing windows into one screen) and
once on the wire (the UI protocol). Both halves are relevant, and the split is the most useful lesson for Visur.

**(a) Layer compositing — `ui_compositor.c`.**
Neovim can have many `ScreenGrid`s: one per window, plus global grids (statusline, cmdline, message area,
multigrid). The compositor's job is to flatten z-ordered layers into the **one grid the UI consumes**, and it does
not send anything itself until a flush:

- Each window owns a grid; the compositor maintains a composed output grid and a z-order list of layers
  ([`ui_compositor.c`](https://github.com/neovim/neovim/blob/master/src/nvim/ui_compositor.c)).
- A window's change invalidates only **the rows that window covers** in the composed grid — not its rectangle, and
  not the screen. Because composition is per-row, two overlapping windows ("floats", popupmenu over text,
  cmdline over statusline) *cannot* produce two overlapping writes: the overlap collapses to one composed row, and
  the topmost layer wins cell-by-cell.
- **Granularity: a contiguous run of cells within one row** (`row`, `col_start`, `cells[]`). Never a rectangle, never
  an individual cell on the wire, never a sub-cell.
- **Merging is therefore implicit**: "region merging" is not a rect-intersection algorithm; it is (i) one composed
  grid, (ii) a per-row dirty bitmap, (iii) coalescing adjacent dirty rows into one sequential flush. Rect merging
  only appears if a renderer keeps *independent* damage rectangles — which is why the simpler architecture is to
  compose first, diff second.
- The upstream damage signal is Vim's `redraw_later`/`VALID_*`/`NOT_VALID` machinery in
  [`drawscreen.c`](https://github.com/neovim/neovim/blob/master/src/nvim/drawscreen.c): a window is `VALID`
  (do nothing), `SOME_VALID` (recompute some rows) or `NOT_VALID` (recompose the window); only changes to the
  *window's content or position* reach the compositor.

**Support:** interpretation of architecture + the documented protocol shape. **Confidence:** high for granularity and
the compose-then-diff ordering; **medium** for the specific function names.

**(b) The wire protocol — `api/ui_events.in.h` / [`:h api-ui-events`](https://neovim.io/doc/user/api-ui-events/).**
The definitive primary source for the shape, and the best template available for Visur's own payload:

```
["grid_line",   grid, row, col_start, cells, wrap]
["grid_scroll", grid, top, bot, left, right, rows, cols]      ← signature confirmed by search snippet
["grid_clear",  grid]
["grid_cursor_goto", grid, row, col]
["grid_resize", grid, width, height]
```

Three design decisions here are directly reusable:

1. **Damage is expressed as *rows of cells*, not as a bitmap.** `grid_line` carries only the run that changed
   (`col_start` + `cells`), so a dirty region is encoded as data, not as coordinates. A renderer needs no
   rectangle algebra: it writes what it is handed.
2. **Bulk movement is a separate verb.** `grid_scroll` describes a *rectangular* region and a signed `rows`/`cols`
   delta, so page-down/insert-lines cost one message instead of a full row dump. Note `rows`/`cols` are signed
   (negative = up/left), which also lets a client use a hardware scroll region (`CSI top;bot r` + `CSI k S`) when
   the terminal supports it and fall back to rewriting cells when it does not.
3. **The cursor is its own event**, sent at most once per batch, and `grid_cursor_goto` is separate from any cell
   write. The client decorates the cursor cell itself (inverse video / `DECSCUSR` / hardware cursor) — it is never
   encoded as a cell attribute in the grid.

One detail to **verify before implementing**: `grid_line` allows trailing blank cells to be omitted and carries a
`wrap` boolean, and the precise client-side obligation for omitted trailing cells (fill blanks vs. leave the old
contents) is stated in the protocol doc. Do not guess it — it changes whether a renderer needs an explicit `EL`.
**Confidence:** medium on the elision rule; **high** that `wrap` exists and that cells are `[text, hl_id, repeat]`
triples.

### 1.3 Kilo — the minimum viable approach, and what actually breaks

[`antirez/kilo`](https://github.com/antirez/kilo/blob/master/kilo.c) (~1 kLOC) has no compositor, no dirty regions and
no diffing at all. `refresh_screen()` is, in outline: hide the cursor → `ESC[H` home → for each visible row, write the
row's text, then `ESC[K`; then position the cursor and show it. One `write()` of the assembled buffer per keystroke,
per frame.

So the **minimum viable terminal renderer** is:

> alternate screen on; raw mode; build a `k`-byte buffer covering the *entire viewport*; one `write`; repeat on every
> keystroke and every resize.

It genuinely works, and it is roughly what [visur.js](../../../../src/main/resources/static/visur.js) already does to a
canvas. What it costs and what it breaks:

| Breakage | Cause | Mitigation that stays "minimum" |
|---|---|---|
| **Tearing / flicker** on fast GPUs (kitty, Ghostty, WezTerm) | the terminal paints the buffer mid-stream; rows appear top-to-bottom over 1–2 frames | wrap in `ESC[?2026h` / `ESC[?2026l` (2 lines of code) — this is the whole fix |
| **Cursor "walking" during paint** | cursor is visible while it is being repositioned | `ESC[?25l` … `ESC[?25h` |
| **Scrollback pollution** | painting into the normal screen | `ESC[?1049h` |
| **Residue at end of line** | old longer line not erased | `ESC[K` per row (Kilo does this) |
| **Attribute bleed** | SGR never reset between rows | `ESC[0m` or per-run diffing |
| **O(viewport) bytes per keystroke, unconditionally** | every row emitted even when unchanged | row hash skip — the cheapest 10 lines of diffing that exist |
| **Wide-glyph corruption** | double-width cell written as two 1-cell writes | keep cell width in the model, never split a wide grapheme |

What Kilo *proves* is the important part: **cost is bounded by the viewport, not the document.** A 10 000-line file
costs the same to paint as a 10-line file. That is precisely the property Visur lacks today, where
`modelWasChanged` ships the *whole buffer* per keystroke
([ModelWasChangedVerticle.java:19-28](../../../../src/main/java/com/ple/visur/ModelWasChangedVerticle.java#L19-28),
[visur.js:72](../../../../src/main/resources/static/visur.js#L72)). The terminal renderer gets "does not scale with
document size" for free from its medium; the payload half is where Visur's problem actually is.
**Support:** interpretation of kilo.c + arithmetic. **Confidence:** high.

### 1.4 tmux — when a multiplexer gives up and repaints

tmux is the useful counter-example because it is *inside* the pipeline: it consumes escape sequences from the
application (so it never receives damage rectangles), and it produces escape sequences to the outer terminal (so it
must do its own diffing).

- **Normal operation is row-granular, cell-level.** tmux keeps a per-cell grid per pane, and on redraw walks the
  visible screen row by row, emitting only rows whose cells differ from what the client last received; within a row
  it coalesces identical-attribute runs and tracks the assumed outer-terminal cursor/attribute state to avoid
  redundant `CUP`/`SGR` ([`screen-redraw.c`](https://github.com/tmux/tmux/blob/master/screen-redraw.c),
  [`tty.c`](https://github.com/tmux/tmux/blob/master/tty.c)). Pane borders/status line are drawn only when dirty.
- **Full redraw triggers** — the cases where tmux throws the diff away. These are the ones that matter for Visur,
  because Visur's client will be *inside* tmux for some users:

  1. **Client resize** (`SIGWINCH` → `server_client_check_resize` → `recalculate_sizes`): every coordinate on screen
     changed, so the diff is meaningless — full repaint of everything.
  2. **Attach / detach / client switch** — the new client's physical screen is unknown.
  3. **Layout change**: pane split/close/zoom, `select-layout`, window switch, pane swap.
  4. **Alternate screen enter/exit** in a pane — the pane's whole coordinate space effectively resets.
  5. **Outer-terminal capability or size-of-charset change**, `refresh-client`, and the explicit `prefix r` binding.
  6. **Status line on/off, or a style/terminal-feature change** (`tty_force_redraw`).

  **Support:** interpretation of the tmux source + the general principle, which is what to carry into the spec.
  **Confidence:** medium on the exact trigger list; **high** on the principle below.

- **The principle to carry forward:** *damage is only incremental while the coordinate space is stable.* Anything that
  changes the grid's dimensions, origin, or terminal mode invalidates the diff by definition. So the renderer needs
  exactly two paths: `diff(cur, next)` and `fullRepaint(next)`, plus a small set of events that force the second
  (resize, alt-screen switch, reconnect, `SIGWINCH`-while-dirty, mode change).

tmux 3.4+ also **supports synchronized output on the app's behalf**: the changelog entry reads "Add support for
applications to use synchronized output mode (DECSET 2026)"
([tmux CHANGES](https://github.com/tmux/tmux/blob/master/CHANGES)), and tmux itself brackets its own redraws. Two
caveats: it must also be able to emit `2026` to the *outer* terminal, which depends on the outer terminal; and
pass-through of app-emitted sequences inside a mux is the historically unreliable path (see §2.4).

### 1.5 Alacritty / kitty / WezTerm — what a terminal will do *for* you

The honest answer for the ticket: **almost nothing.** A terminal emulator is a VT parser with a framebuffer; it has
no concept of "this region is dirty" and no API to accept a damage rectangle. The renderer is on its own for diffing.

What terminals *do* accept that reduces work:

| Hint | Sequence | Reduces bytes? | Reduces perceived work? |
|---|---|---|---|
| **Synchronized update** | `ESC[?2026h` … `ESC[?2026l` (a.k.a. BSU/ESU) | No | **Yes — kills tearing and mid-frame flicker.** The one hint worth having. |
| Hardware scroll region | `ESC[{top};{bot}r` + `ESC[{n}S`/`ESC[{n}T` | Yes (huge) for whole-screen scrolls | Yes |
| Insert/delete line & char | `ESC[{n}L`, `ESC[{n}M`, `ESC[{n}P`, `ESC[{n}@` | Yes (huge) for in-line edits | Yes |
| `DECSCUSR` cursor shape | `ESC[{n} q` | n/a | Cosmetic, cheap |
| Kitty keyboard protocol | `ESC[>{flags}u` | n/a | No — but it removes key-ambiguity (relevant to a *later* ticket, not this one) |
| Kitty graphics / sixel / iTerm2 images | APC/DCS | n/a | Out of scope for Visur |

- Alacritty: "Synchronized updates now use `CSI 2026` instead of legacy DCS variant" in
  [0.13.0](https://alacritty.org/changelog_0_13_0.html) — so Alacritty has supported it since 2023, via `CSI ?2026`.
- kitty: documents it and states plainly that "konsole, gnome-terminal and xterm do not support the Synchronized
  update" ([kitty performance](https://sw.kovidgoyal.net/kitty/performance/)).
- Ghostty: has a user-facing doc for it precisely because its fast GPU renderer makes tearing *visible*
  ([Ghostty: synchronized output](https://ghostty.org/docs/help/synchronized-output)).
- WezTerm: supports it (see the support matrix discussion below), though one third-party capability detector
  distinguishes "WezTerm-non-mux", i.e. the mux path is where it is lost.

**Support:** direct evidence (snippets from the Alacritty changelog and kitty's perf page). **Confidence:** high that
2026 is the only meaningful "rendering hint"; high that it does not reduce byte count.

---

## 2. Half B — terminal capability baseline on macOS

### 2.1 Capability matrix

"Assume?" = may be used without querying. Everything in the "must query" rows needs either an environment check, a
terminfo lookup, or a `DECRQM` probe (see §2.5).

| Capability | Sequence | Assume? | Evidence / notes |
|---|---|---|---|
| Cursor addressing | `CUP` `ESC[{row};{col}H` | **Yes** | VT100 (1978); terminfo `cup`. Universal. `ESC[H` = home. |
| Erase in display | `ED` `ESC[2J` | **Yes** | terminfo `clear`/`ed`. Also `ESC[3J` (erase scrollback) — *not* universal, and not needed. |
| Erase in line | `EL` `ESC[K` | **Yes** | terminfo `el`; the single most valuable byte-saving primitive. |
| Cursor show/hide | `ESC[?25l` / `ESC[?25h` | **Yes** | DECTCEM; terminfo `civis`/`cnorm`. |
| SGR basics (bold, underline, reverse, 8/16 colour) | `ESC[…m` | **Yes** | terminfo `sgr0`, `bold`, `rev`, `setaf`. |
| 256 colour | `ESC[38;5;{n}m` | **Verify, cheaply** | Available on essentially any `xterm-256color`; verify via `TERM`/terminfo `colors`. macOS Terminal.app supports it. |
| Truecolour (24-bit) | `ESC[38;2;{r};{g};{b}m` | **No — must query** | Check `COLORTERM` ∋ `truecolor`/`24bit`, or terminfo `Tc`/`RGB`. See §2.3. |
| Alternate screen | `ESC[?1049h` / `l` | **Yes** | xterm; see §2.2 for what it does to scrollback. |
| Synchronized output | `ESC[?2026h` / `l` | **No — must query** | See §2.4. Widely available, notably **not** in Terminal.app (evidence gap — §4). |
| Bracketed paste | `ESC[?2004h` / `l`, paste wrapped in `ESC[200~` … `ESC[201~` | **Query, but always enable** | See §2.6. |
| Mouse reporting | `ESC[?1000h`, `1002`, `1003`, `1006` (SGR) | **Opt-in only** | Never enable unrequested; `1006` vs legacy `1015` differs. |
| Resize signal | `SIGWINCH` | **Yes** (POSIX) | See §2.7. |
| Cursor position report | `DSR` `ESC[6n` → `ESC[{row};{col}R` | **Yes, but read with a timeout** | See §2.7 for why this is a trap on macOS. |
| Window size query | `ioctl(TIOCGWINSZ)` | **Yes** | The correct primary source of size; `SIGWINCH` only says "it changed". |
| Device attributes | `ESC[c` / `ESC[>c` | Yes | Used as a terminal *identity* probe; not needed for the above. |

**Support:** direct evidence for the standard control functions (xterm
[ctlseqs](https://invisible-island.net/xterm/ctlseqs/ctlseqs.html) is the canonical registry for all of the
`?`-prefixed ones); interpretation for the "assume?" judgements.

### 2.2 Alternate screen (`?1049`) and scrollback

`ESC[?1049h` = *save cursor, switch to the alternate screen, clear it*; `ESC[?1049l` = *restore cursor and the
original screen*. Consequences the client must be designed around:

- **The alternate screen has no scrollback** in the sense users expect: content painted there is not added to the
  main scrollback buffer, and on exit the *original* screen and its scrollback are restored verbatim. This is the
  desired behaviour for an editor (no scrollback pollution) and it is what vim/nvim/less/htop all do.
- **Consequence for Visur's scroll model:** because `?1049` blocks the terminal's own scrollback, Visur cannot rely
  on the terminal to scroll the document — page-up/page-down must be a *server-side viewport change* re-rendered as
  cells (or, at best, as a bounded `grid_scroll`). This directly informs the map's open question about
  `canvasStart`/`canvasEnd`/`fy` mapping to a terminal.
- **On exit, always `?1049l`** before restoring termios — and on a signal path too, or the user's shell is left on a
  wrecked alternate screen.
- **Inside a mux**, `?1049` applies to the *pane*, so a tmux pane can be on the alternate screen while the outer
  terminal is not. Nested semantics are the multiplexer's problem, but the pane's scrollback behaviour changes.
- Variants exist (`?1047`, `?1048`, `?47`); `?1049` is the one to use — it is the only one that combines save-cursor
  with clear-on-enter, which is what avoids a flash of the previous screen's contents.

**Support:** interpretation of ctlseqs + general practice. **Confidence:** high.

### 2.3 Truecolour

- Detection is `COLORTERM=truecolor` or `COLORTERM=24bit`, or terminfo capability `Tc` (some `RGB`). `COLORTERM` is
  a convention maintained by the [termstandard/colors](https://github.com/termstandard/colors) effort, not a standard.
- **macOS specifics, time-sensitive:** the historical consensus — and the reason for years of "use iTerm2 on macOS"
  advice — is that Apple's **Terminal.app did not support 24-bit colour**. That changed recently: Terminal.app in
  **macOS Tahoe (macOS 26, announced WWDC June 2025)** was reported to gain 24-bit colour and Powerline font support
  ([MacRumors, 2025-06-16](https://www.macrumors.com/2025/06/16/apples-terminal-app-macos-tahoe/) — secondary source,
  Apple's own release notes not located in this run). On older macOS versions and on any Terminal.app in
  `TERM_PROGRAM=Apple_Terminal` with `COLORTERM` unset, assume 256-colour only.
- **Design consequence:** treat colour as a *quantised* capability. The view protocol should carry semantic colour
  (palette index or RGB triple) and let the renderer downgrade RGB → nearest 256/16. Do not let the server assume a
  bit depth; that is exactly the kind of thing the out-of-process boundary exists to prevent leaking.

**Support:** direct evidence (search snippets) for the ecosystem convention; **secondary only** for the Terminal.app
claim. **Confidence:** medium.

### 2.4 Synchronized output (`?2026`)

- **What it does:** `ESC[?2026h` tells the terminal "buffer everything until `ESC[?2026l`", then present the completed
  frame in one go; terminals also enforce a **timeout** so a crashed app cannot freeze the display. It eliminates
  **tearing** (rows painted in scan-line order) and mid-frame flicker on a full repaint. It does **not** reduce the
  number of bytes, does **not** make the frame arrive faster, and does **not** remove the need for diffing.
- **Origin:** iTerm2 introduced it as a DCS sequence; the `CSI ?2026` (DECSET private mode) form is the
  interoperable version. Contour's spec gist documents both and is the closest thing to a written spec
  ([Terminal Spec: Synchronized Output](https://gist.github.com/christianparpart/d8a62cc1ab659194337d73e399004036)).
- **Availability (as of the evidence gathered here):** kitty, iTerm2, WezTerm, Alacritty (≥0.13), Ghostty, foot,
  Contour, Windows Terminal, VS Code's integrated terminal, tmux ≥3.4 (on the app's behalf). kitty's own docs
  exclude **konsole, gnome-terminal and xterm**. There is at least one conflicting third-party claim about xterm (see
  Contradictions). **Terminal.app: no evidence of support found in this run** — treat as unsupported.
- **The correct handling is a probe, not a table.** Send `DECRQM` (`CSI ? 2026 $ p`) and read the reply:
  `CSI ? 2026 ; 1 $ y` = set/supported, `; 2 $ y` = reset/supported, `; 0 $ y` = not recognised, silence = no
  `DECRQM` at all. The Contour gist states the decision rule directly: *"If you get nothing back (DECRQM not
  implemented at all) or you get back a `CSI ? 2026 ; 0 $ y` then synchronized output is [not supported]"*.
  **Support:** direct evidence. **Confidence:** high.
- **Fallback when unsupported:** hide the cursor, single `write`, paint in top-to-bottom row order (so the frame
  "wipes" downward like a scroll rather than jumping), and accept flicker. Do not skip the repaint.
- **Mux caveat:** inside tmux ≥3.4 the app's `2026` is understood by tmux; whether the *outer* terminal flicks is
  then tmux's business. Inside older tmux (or `screen`), `2026` is silently ignored, so the app must not assume the
  batching happened. Its own escaping must also be correct — an app that emits `2026h` and then dies mid-frame relies
  on the terminal's timeout.

### 2.5 What must be queried, and how

Three layers, cheapest first:

1. **Environment** (0 ms): `TERM`, `TERM_PROGRAM`, `TERM_PROGRAM_VERSION`, `COLORTERM`, `NO_COLOR`, `TMUX`/`STY`,
   `COLORTERM`. Gives you a *guess* at the family and, crucially, tells you if you are inside a multiplexer (which
   changes who owns the alt screen and whether `2026` reaches the real terminal).
2. **`TIOCGWINSZ`** (a syscall): the authoritative viewport size. Never guess size from `$COLUMNS`/`$LINES`.
3. **Terminal probes** (need a raw-mode read with a deadline): `DECRQM` for `2026`; optionally `DA1` (`ESC[c`) for
   identity, `DSR 6` (`ESC[6n`) for cursor position. Each probe costs one round-trip through the pty, so **probe once
   at startup, in one batch, with a ~100–200 ms timeout**, and never on the keystroke path.

The pragmatic policy for Visur: **assume the VT100/ANSI core, probe `2026` and truecolour, degrade silently.** Every
degradation must produce a *correct* screen, only a less pretty one.

### 2.6 Bracketed paste (`?2004`)

- `ESC[?2004h` makes the terminal wrap pasted text in `ESC[200~` … `ESC[201~`. Without it, a paste arrives as a stream
  of ordinary key bytes — so `}` or a `dd` in pasted text gets *executed*, newlines become "Enter" commands, and any
  auto-indent/bracket-completion logic mangles the text.
- **It must be enabled** by any program that accepts text input and distinguishes paste from typing. It was added to
  xterm in 2005-ish and is supported by every macOS terminal in practical use (Terminal.app, iTerm2, kitty, WezTerm,
  Ghostty, Alacritty).
- **Single most important consequence for Visur:** paste is **one event carrying N characters**, not N key events.
  A terminal client that forwards keystrokes one at a time (the browser client's model,
  [visur.js:132-134](../../../../src/main/resources/static/visur.js#L132-L134)) will send one `KeyPressed` per pasted
  character and trigger one full model round-trip *per character*. This is a protocol requirement, not a nicety: the
  wire format needs a "text inserted" message, or bracketed paste will be catastrophically slow even before it is
  correct. **This is the strongest finding in Half B for ticket 03.**
- Also: the paste payload must be sent as *text*, not re-encoded into key tokens, and `ESC[201~` must be stripped
  before insertion.

**Support:** direct evidence for the mechanism; interpretation for the Visur consequence. **Confidence:** high.

### 2.7 `SIGWINCH` and `DSR`/`CPR`

- **`SIGWINCH`** is delivered to the foreground process group when the pty's window size changes (POSIX; the kernel
  raises it from `TIOCSWINSZ`). On receipt: `ioctl(TIOCGWINSZ)` to read the new `ws_row`/`ws_col`, then full repaint
  (see §1.4 — every coordinate changed). Practical hazards:
  - Signals **coalesce**: three fast resizes can produce one `SIGWINCH`. Always re-read the size; never assume the
    size is "one notch" different from the last signal.
  - Dragging a window edge produces a *storm* of these. Debounce (~30–50 ms) or you will repaint dozens of times per
    second and stutter — and each repaint is a full viewport.
  - Use `sigaction` + a self-pipe or `signalfd`/`EVFILT_SIGNAL` (kqueue on macOS), not a handler that calls into the
    renderer. On macOS the natural pairing is `kqueue`/`EVFILT_SIGNAL` with a poll loop over stdin + signal fd.
  - `SIGWINCH` is *not* delivered when attaching to an existing tmux session at a different size in every case; a
    startup `TIOCGWINSZ` is mandatory, and one extra repaint after the first input event is cheap insurance.
- **`DSR`/`CPR`** (`ESC[6n` → `ESC[{row};{col}R`) is the fallback for "where is the cursor" when you do not trust your
  own model (e.g. after `SIGWINCH` racing a repaint, or after another process wrote to the same tty). Because the
  reply arrives **interleaved with user input on the same fd**, it requires either (a) a parser that recognises
  `ESC[…R` anywhere in the input stream and routes it out of the key path, or (b) a read deadline. For a full-repaint
  renderer that always emits absolute `CUP`, **CPR is not needed** — this is a real simplification available to
  Visur, and it should be recorded as a deliberate non-requirement. Keep it only for the resize-race repair path if a
  corruption bug shows up.
- **Cursor position tracking generally:** since the renderer knows every cell it wrote, it can track the terminal's
  cursor exactly. That is what makes relative motion (§1.1 #5) safe. It is also why a renderer must never let an
  un-modelled write happen — one stray `printf` desynchronises the model.

**Support:** direct evidence for the mechanism/POSIX semantics; **interpretation** for the hazards. **Confidence:**
high on mechanism, medium on the macOS-specific signal-loop recommendation.

### 2.8 Is a full viewport repaint per keystroke acceptable?

The ticket asks the same question about the terminal that [Baseline measurements](../issues/01-baseline-measurements.md) asks
about canvas. Answer, split into the three things that actually decide it:

**1. Bytes.** All arithmetic, viewport-bounded, nothing to do with document size:

| Viewport | Cells | Text bytes | + `CUP`/row | + per-row `EL` | Naïve per-cell SGR (worst case) |
|---|---|---|---|---|---|
| 80×24 | 1 920 | ~1.9 KB | ~0.2 KB | ~0.1 KB | ~19 KB |
| 120×40 | 4 800 | ~4.8 KB | ~0.4 KB | ~0.2 KB | ~48 KB |
| 200×60 | 12 000 | ~12 KB | ~0.6 KB | ~0.3 KB | ~120 KB |

With SGR diffing and `EL` collapsing, a realistic full repaint of a *text* screen lands in the low single-digit KB.
**Even the worst case is under 130 KB.**

**2. Time.** Also arithmetic: a local pty plus a GPU terminal processes on the order of tens of MB/s; 5 KB is
therefore well under a millisecond, and 100 KB is a few milliseconds — inside a 16 ms frame. The failure mode is not
the terminal, it is **the path**: over SSH at ~10 Mbit/s, 5 KB is ~4 ms and 100 KB is ~80 ms (five dropped frames);
through a mux plus a slow link it is worse, and latency (not bandwidth) is usually the binding constraint. So:

> **A full viewport repaint per keystroke is acceptable on a modern terminal, locally, up to roughly 200×60 — provided
> it is wrapped in `?2026`, on the alternate screen, with the cursor hidden.** It becomes visibly bad on
> remote/muxed links, and it is the *ping-pong* of a round-trip per keystroke, not the paint, that dominates.

**3. The comparison that actually matters for Visur.** Paint is bounded by the viewport; **payload today is bounded
by the document** (`modelWasChanged` serialises the whole buffer,
[ModelWasChangedVerticle.java:19-28](../../../../src/main/java/com/ple/visur/ModelWasChangedVerticle.java#L19-L28)).
At 10 000 lines the payload is orders of magnitude larger than any full repaint in the table above, and it grows with
the file while the repaint does not. This is consistent with — and gives a directional answer to — the map's standing
preference *"measure before optimizing"*: the terminal path inherits viewport-bounded paint for free, so the number the
baseline ticket needs to produce is about **payload**, and the expected answer is that paint does not need dirty
rectangles for beta while the payload does need a viewport slice.

Residual risk on this finding: it is arithmetic, not measurement. Nothing here replaces
[Baseline measurements](../issues/01-baseline-measurements.md); it predicts its result.

---

## 3. What the ticket's explicit questions resolve to

| Question | Answer |
|---|---|
| Cell/row/grid granularity in Neovim | **Run-of-cells within a row** (`grid_line`: `row`, `col_start`, `cells[]`), plus **rectangular `grid_scroll`** for bulk movement, plus a **separate cursor event**. Composition happens on one flattened grid, so overlapping windows merge per-row rather than as rect intersection. |
| Kilo's minimum approach | **Full viewport repaint per frame**, no diff: alt-screen + `ESC[H` + row text + `ESC[K` + cursor, one `write`. Works; breaks on flicker, cursor walk, and unconditional O(viewport) bytes. |
| tmux full-redraw triggers | **Any change to the coordinate space**: resize, attach/detach, layout/pane change, alt-screen enter/exit, status/style/terminal-feature change, explicit refresh. Within a stable coordinate space it redraws per-row diffs only. |
| Terminal rendering hints that help | **`?2026` is the only one that changes perceived quality** (kills tearing; no byte savings). Scroll/insert-delete-line escapes are the only ones that save real bytes. No terminal accepts a damage rectangle. |
| `?1049` and scrollback | **Yes, assume it.** It removes the terminal's scrollback from the equation — so **document scrolling must be server-side viewport re-render**, not terminal scroll. |
| `?2026` availability and effect | Wide but **must be probed** (`DECRQM`); kills flicker on full repaint, does **not** cut bytes or latency. Terminal.app: assume absent. |
| `?2004` bracketed paste | Assume/always enable. For Visur it forces a **"paste as one text-insert event"** message on the wire. |
| Truecolour | **Must query** (`COLORTERM`, `Tc`); Terminal.app only from macOS Tahoe. Design the payload for RGB and downgrade in the renderer. |
| `SIGWINCH` | Assume; pair with `TIOCGWINSZ`, debounce, coalesce, and trigger a full repaint. `CPR` is **not required** for an absolute-`CUP` renderer. |
| Full repaint per keystroke | **Acceptable** locally to ~200×60 with `?2026`; not acceptable as a *round-trip per keystroke* over a mux/remote link. The payload, not the paint, is the bottleneck. |

---

## 4. Contradictions and disputed evidence

1. **Does xterm support `?2026`?** kitty's performance page lists "konsole, gnome-terminal and xterm" as *not*
   supporting synchronized updates; a bug report snippet from another project lists "xterm, alacritty, foot, kitty,
   iTerm2, Wezterm" as expected to support it. **Unresolved.** Does not affect the recommendation (probe, don't
   table-lookup), and does not affect Visur on macOS.
2. **Alacritty and `?2026`.** One terminal-comparison doc states "Alacritty does not — it has no …" (truncated
   context) while Alacritty's own 0.13.0 changelog says "Synchronized updates now use `CSI 2026` instead of legacy DCS
   variant". The **primary source (Alacritty's changelog) wins**: Alacritty supports `CSI ?2026`. Recorded as resolved
   in favour of the changelog, because a changelog entry is first-party and the comparison doc is not.
3. **Terminal.app truecolour.** Long-standing "Terminal.app has no truecolour" is still repeated in 2023-era
   write-ups, while a June 2025 report says macOS Tahoe adds it. Both may be true at different dates. **Recorded as
   time-sensitive**; do not hard-code either.
4. **`?2026` inside tmux.** One source says tmux 3.4+ supports app `2026` unconditionally (tmux CHANGES), another
   says "when configured". The difference is likely `terminal-features`/outer-terminal support rather than an opt-in
   switch. **Unresolved**; the mitigation is the same (never assume batching succeeded).

---

## 5. Missing evidence

1. **No primary-source text was opened in this run** (no fetch tool). Source-file claims about Neovim/tmux/ncurses are
   architecture-level and marked as such. **Before writing the spec, open
   [`ui_compositor.c`](https://github.com/neovim/neovim/blob/master/src/nvim/ui_compositor.c),
   [`api-ui-events`](https://neovim.io/doc/user/api-ui-events/) and
   [`screen-redraw.c`](https://github.com/tmux/tmux/blob/master/screen-redraw.c) and confirm the specifics.** This is
   the largest residual risk in this document.
2. **`grid_line` trailing-cell/`wrap` semantics** — the exact client obligation is not confirmed. Must read
   `api-ui-events` before implementing.
3. **Terminal.app `?2026` support** — no evidence either way; assumed absent.
4. **No measurements.** The byte/time table in §2.8 is arithmetic. Nothing was profiled, and no bytes-per-second
   figure was verified from a primary benchmark. [Baseline measurements](../issues/01-baseline-measurements.md) still owns the
   numbers.
5. **macOS system terminfo** — whether the shipped `/usr/share/terminfo` in current macOS carries `Tc`/`RGB` and
   `Sync` entries was not verified. If it does not, an app that consults terminfo (rather than probing) will
   under-detect on a stock Mac. Not verified in this run.
6. **Wide-glyph / grapheme-cluster handling** (CJK, emoji, combining marks, `?2027`) is out of scope here but is a
   real correctness risk for a cell-grid renderer; flagged for a later ticket.
7. **Behavior of `?2026` when the client is killed mid-frame** (terminal timeout length, per terminal) not verified.

---

## 6. Implications for the downstream tickets

For [Decide the view-update protocol and where its seam sits](../issues/03-decide-view-protocol.md):

- The payload should look like Neovim's, not like a document: **a viewport slice of cells plus an explicit change
  kind**, with at least `full`, `rows` (dirty row runs), and `text-inserted` (bracketed paste) shapes. Neovim's
  `grid_line`/`grid_scroll`/`grid_cursor_goto` triple is a proven vocabulary already implemented by at least four
  clients.
- **Paste needs its own message** (§2.6) or bracketed paste degenerates into N round-trips.
- Cells should carry **semantic** colour and attributes, with downgrading in the renderer (§2.3).
- Damage can be expressed as **row runs** rather than rectangles; that removes rect-intersection from the design
  entirely (§1.2).

For [Decide the terminal client's shape, transport, and parity](../issues/08-decide-terminal-shape.md):

- The capability baseline is: **assume VT100/ANSI core + `?1049` + `?2004` + `SIGWINCH`; probe `?2026` and
  truecolour once at startup; degrade silently.** No other feature detection is required for beta.
- The client needs exactly two paint paths — `diff` and `fullRepaint` — plus a small force-repaint event set
  (resize, alt-screen switch, reconnect, mode change).
- The client needs a single `write(2)` per frame, a raw-mode read loop, a signal-aware event loop (kqueue on macOS),
  and cursor/attribute state tracking. That is the whole renderer core — on the order of a few hundred lines,
  independent of language, which is consistent with the settled decision that the client lives outside the JVM.

---

## Sources

**Kept**

- [Neovim UI protocol / `api-ui-events`](https://neovim.io/doc/user/api-ui-events/) — authoritative description of
  `grid_line`, `grid_scroll`, `grid_clear`, `grid_cursor_goto`; `grid_scroll` signature confirmed by search snippet.
- [neovim `src/nvim/ui_compositor.c`](https://github.com/neovim/neovim/blob/master/src/nvim/ui_compositor.c) —
  the layer-compositing implementation; owns the "compose then diff per row" claim.
- [neovim `src/nvim/drawscreen.c`](https://github.com/neovim/neovim/blob/master/src/nvim/drawscreen.c) — upstream
  damage marking (`VALID_*`/`redraw_later`).
- [neovim `src/nvim/api/ui_events.in.h`](https://github.com/neovim/neovim/blob/master/src/nvim/api/ui_events.in.h) —
  the definition file that generates the protocol docs; the most precise source for the event shapes.
- [`antirez/kilo` kilo.c](https://github.com/antirez/kilo/blob/master/kilo.c) — the minimum viable full-repaint
  renderer.
- [tmux `screen-redraw.c`](https://github.com/tmux/tmux/blob/master/screen-redraw.c) and
  [`tty.c`](https://github.com/tmux/tmux/blob/master/tty.c) — pane redraw and output-diffing implementation.
- [tmux `CHANGES`](https://github.com/tmux/tmux/blob/master/CHANGES) — "Add support for applications to use
  synchronized output mode (DECSET 2026)".
- [ncurses `tty_update.c`](https://github.com/mirror/ncurses/blob/master/ncurses/tty/tty_update.c) and
  [`curs_refresh(3x)`](https://invisible-island.net/ncurses/man/curs_refresh.3x.html) — the reference cell-grid diff
  (`doupdate`), line hashing, cursor/attribute state tracking.
- [xterm ctlseqs](https://invisible-island.net/xterm/ctlseqs/ctlseqs.html) — canonical registry for `CUP`, `ED`,
  `EL`, `SGR`, `?1049`, `?2004`, `?2026`, `DSR`/CPR, mouse modes.
- [Alacritty 0.13.0 changelog](https://alacritty.org/changelog_0_13_0.html) — first-party confirmation that Alacritty
  speaks `CSI ?2026`.
- [kitty performance](https://sw.kovidgoyal.net/kitty/performance/) — states which terminals lack synchronized updates.
- [Ghostty: synchronized output](https://ghostty.org/docs/help/synchronized-output) — user-facing explanation of the
  tearing problem on fast renderers.
- [Contour spec gist: Synchronized Output](https://gist.github.com/christianparpart/d8a62cc1ab659194337d73e399004036)
  — closest thing to a written spec for `?2026` and the `DECRQM` probe rule.
- [iTerm2 downloads/changelog](https://iterm2.com/downloads.html) — "Add DECSET 2026 for synchronized updates".
- [MacRumors: Terminal.app in macOS Tahoe gains 24-bit colour](https://www.macrumors.com/2025/06/16/apples-terminal-app-macos-tahoe/)
  — secondary, but the best available source for a recent Apple change; flagged as such.
- [terminfo.src (invisible-island)](https://invisible-island.net/ncurses/terminfo.src.html) — what capability names
  (`Tc`, `RGB`, `Sync`, `cup`, `el`) actually exist in the database nvim/tmux consult.

**Rejected / deprioritized**

- "Build your own text editor" (snaptoken) — a tutorial *about* kilo; kilo.c itself is the primary source.
- Terminal-comparison aggregators (terminaltrove, otty, terminal guide blogs) — useful for spotting which terminals
  might support `?2026`, but not authoritative; the contradictions they introduced are recorded in §4 and resolved
  only where a first-party changelog exists.
- "Why Claude Code flickered: DEC mode 2026, cell diffs and a 16 ms frame budget" (dev.to, 2026) — a secondary write-up
  whose conclusions align with this survey, but it is an explanation of someone else's bug, not a spec. Not cited for
  any claim above.
- Medium / DEV.to posts on macOS truecolour — superseded by the primary ecosystem sources and by the date-sensitive
  Tahoe change.
- Anything about Java TTY APIs (JLine, `System.console()`, Lanterna) — out of scope: the client is a separate
  non-JVM process by settled decision.
