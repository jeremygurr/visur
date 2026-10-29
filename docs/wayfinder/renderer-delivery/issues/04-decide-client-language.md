# Decide the terminal client's implementation language and TUI stack

Type: grilling
Status: open

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
