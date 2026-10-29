# Decide the terminal client's implementation language and TUI stack

Type: grilling
Status: closed
Closed: 2026-10-28
Assignee: pi (agent session, 2026-10-28)

## Question

The client runs outside the JVM (settled — see [Decide the terminal client's process boundary and transport](05-decide-process-boundary.md)). So what is it written in, and on top of what?

This is not a language-taste question. The stack decides several things this map has already committed to, and the answer must be argued against them.

**Candidates, roughly cheapest-to-most-control:**

- **Go + tcell / bubbletea** — `tcell` owns the screen and does its own cell diffing; you get a compositor but hand over escape-sequence control.
- **Rust + ratatui + crossterm** — `ratatui` is a buffer-diffing widget layer over backend escape output; `crossterm` is the raw mode/input/escape library. You keep the escape sequences and the cell grid is *yours*, which is what the protocol ticket needs.
- **Rust + `libvaxis`/`notcurses`-style** — heavier, more capable (graphics protocols, better damage tracking), more to learn and less portable downlevel.
- **Zig + `libvaxis` / C + `notcurses`** — maximal control, thinnest ecosystem, and Visur is the only thing in the repo written in it.

**What decides it — argue each candidate against these, in this order:**

1. **Does the client own the escape sequences?** The map may well settle on a cell-grid protocol where *the client* decides what to repaint. A stack that owns the screen (`tcell`, `bubbletea`) makes that decision for you and hides exactly the thing being specified. This is the strongest constraint.
2. **Can it hold the previous screen as a cell array cheaply** — contiguous `u32`/struct grid, no per-cell allocation, no GC pause in the paint path — with truecolor SGR.
3. **Synchronized output (`DEC 2026`)** — is it reachable from the stack, or does the stack suppress it? Relevant to whether a full viewport repaint per keystroke is acceptable (same question the canvas asks).
4. **Distribution.** `./gradlew run` must stay the single entry point for a developer. That means a single static binary committed/built, or a per-platform build step, or a fallback path. Java-with-another-JVM-launch is not available. Weigh honestly: this is the main cost of leaving the JVM.
5. **Cost of a hand-rolled protocol client.** No ecosystem ships a Vert.x event-bus wire-format client in these languages — but the transport question is still open, and a plain JSON framing may be chosen precisely so the answer here is "JSON + a socket, both languages have that".

**Do not decide here**: the transport, the payload, or the parity split. Those are [Decide the terminal client's shape, transport, and parity](08-decide-terminal-shape.md) and [Decide the view-update protocol and where its seam sits](03-decide-view-protocol.md).

Bring [Survey of incremental rendering and terminal capability baselines](02-terminal-render-survey.md)'s findings on what the escape-sequence layer must do; they make the "owns the sequences" constraint concrete.

The answer records: the chosen language and libraries, the argument that ruled out each alternative, and an explicit statement of what the client now owns versus what a TUI library owns. Downloading and trying the top two candidates for ten minutes each is expected, not optional — a stack that fights you at `crossterm`/`tcell` level will fight for months.

## Answer

**Rust, minimal tier: crossterm for the terminal boundary, everything above it hand-rolled.** Decided with the user 2026-10-28, at the point where the first line of the client gets written.

Component design, responsibilities, and the server contract are in [Terminal client: components, responsibilities, and the server contract](../design/terminal-client-components.md) — eleven components, the two message flows (`modify`, `get`), and a per-stage latency budget that adds to ~2.1 ms.

### What the client owns, and what crossterm owns

That boundary is the decision, so it is stated exactly:

| Crossterm owns | The client owns |
|---|---|
| raw mode / `enable_raw_mode` | the cell grid (`Vec<Cell>`) |
| stdin byte parsing → `KeyEvent` (ESC-vs-prefix timeout, Alt, modifier combos, bracketed-paste markers) | the damage model (diff of previous vs next grid, **row runs, never rectangles**) |
| `SIGWINCH` delivery and terminal size (`cols`, `rows`) | every escape byte emitted (`CUP`, `SGR`, `EL`, `?1049`, `?2026`, `?25`) |
| — (available but unused: `Command`/`queue!` escape helpers) | the paint policy (two paths: `diff`, `full_repaint`; force-full triggers) |
| | the protocol codec and the key adapter |

Crossterm is therefore an *entry-level* dependency. Nothing downstream of component 1 is a library's decision, which is what keeps the repaint half of the spec testable and what lets a canvas renderer honour the same contract.

### Ruled out, and why

- **`ratatui` (Rust framework)** — owns the buffer diff *and* the backend, i.e. exactly the components the protocol is trying to specify. Adopting it would make the spec's repaint contract "whatever ratatui does", untestable from the canvas side. Reversible in one `cargo add`; not now.
- **`tcell` / `bubbletea` (Go)** — both own the screen; choosing Go would have meant hand-rolling against `golang.org/x/sys/unix` with no crossterm equivalent, so Go's simplicity would have been paid back at the worst possible layer. Go's toolchain is installed, but the mature Go stack fails the "does the client own the escape sequences" constraint.
- **Zig + `libvaxis`** — the most capable option on paper (2,034★, actively developed, real damage tracking) and closest to the "lower level, more powerful" instinct. Rejected on cost, not merit: new pre-1.0 toolchain (not installed), no ecosystem in this repo, and libvaxis hides the compositor in the same way ratatui does.
- **Hand-rolled VT parser** — buys nothing except ownership of the ESC-timeout bug class.

### Two requirements recorded with the decision

1. **Input decoding uses crossterm's parser; the adapter is ours.** crossterm's `KeyCode`/`KeyEvent` vocabulary is neither the browser's `event.key` names nor terminal bytes, so a translation table is unavoidable in every design. That table is [Terminal input and output from an out-of-JVM client](06-terminal-io-non-jvm.md)'s artifact. One thing that ticket must confirm: suppress `KeyEventKind::Release` and collapse `Repeat`, because Visur's keymap has only ever seen browser `keydown`.
2. **One command starts everything.** `visur` probes the port, uses an already-running server, and spawns one if absent — and shuts down only a server it started. Readiness is a successful handshake, not an open port, because Gradle's JVM takes seconds to initialise the editor model. Design in §5 of the component doc; the packaging choice (fat jar vs `./gradlew run`) lands in the spec.

### Consequences recorded elsewhere

- The client is outside the JVM, so the key vocabulary must cross a process boundary — [Decide the terminal client's process boundary and transport](05-decide-process-boundary.md).
- The component doc exposed a boundary hole that this ticket could not close: **who wraps lines**. The browser wraps ([visur.js:167-171](../../../../src/main/resources/static/visur.js#L167-L171)), so an unwrapped viewport slice is not a viewport. Recommendation recorded there (server wraps) and handed to [Decide the view-update protocol and where its seam sits](03-decide-view-protocol.md), which now carries it as a sub-question.
- Language choice does not settle transport — [Decide the terminal client's shape, transport, and parity](08-decide-terminal-shape.md) still owns it, and the design is written against a `Transport` trait so it stays swappable.
