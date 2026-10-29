# Terminal client: components, responsibilities, and the server contract

Design asset for [Decide the terminal client's implementation language and TUI stack](../issues/04-decide-client-language.md). Concrete enough to argue with, and deliberately **not** a spec: the transport, the payload shape, and the parity split are still owned by [Decide the view-update protocol and where its seam sits](../issues/03-decide-view-protocol.md) and [Decide the terminal client's shape, transport, and parity](../issues/08-decide-terminal-shape.md). Where this document touches them it says so.

Target: **vim parity** — keystroke to last cell painted in ~1–2 ms. The measured canvas floor of ~17.5 ms is a SockJS/browser property, not Visur's; a loopback socket round trip is ~0.1–1 ms, so the budget is reachable if every stage below stays small.

## Shape of the thing

One process, one thread, one event loop. The client is a **stateless painter**: it holds no editor state, applies no editing logic, and never derives anything the server has not already said. It holds exactly two things of its own — the last view the server sent, and the screen it last painted.

```
            stdin (bytes)                                    TCP / unix socket
                 │                                                    │
                 ▼                                                    ▼
        ┌─────────────────┐                                 ┌──────────────────┐
        │  1  Key decoder │                                 │  9  Transport    │
        │  (crossterm)    │                                 │  (trait; TBD 08) │
        └────────┬────────┘                                 └────────┬─────────┘
                 │ KeyEvent                                           │ bytes
                 ▼                                                    ▼
        ┌─────────────────┐                                 ┌──────────────────┐
        │  2  Key adapter │───── KeyToken ─────┐            │ 10  Protocol     │
        └─────────────────┘                    │            │     codec (serde)│
                                               ▼            └────────┬─────────┘
                                     ┌──────────────────┐            │ ViewUpdate
                                     │ 8  Transport out │            ▼
                                     └──────────────────┘   ┌──────────────────┐
                                                            │  3  View shadow  │
                                                            └────────┬─────────┘
                                                                     │ + terminal size
                                                                     ▼
                                                            ┌──────────────────┐
                                                            │  4  Screen model │
                                                            │     (cell grid)  │
                                                            └────────┬─────────┘
                                                                     ▼
                                                            ┌──────────────────┐
                                                            │  5  Compositor   │  prev grid
                                                            │     (damage)     │◄──────────┐
                                                            └────────┬─────────┘           │
                                                                     │ row runs             │
                                                                     ▼                      │
                                                            ┌──────────────────┐          │
                                                            │  6  Escape       │          │
                                                            │     emitter      │──────────┘
                                                            └────────┬─────────┘
                                                                     │ one write(2)
                                                                     ▼
                                                                   stdout
```

Numbers are legend, not order of execution; §7 (lifecycle/loop) is the thing that runs them.

## Components

| # | Component | Responsibility | Explicitly *not* its job |
|---|---|---|---|
| 1 | **Key decoder** | Turn raw stdin bytes into semantic `KeyEvent`s. Crossterm does this: ESC-vs-prefix timeout, modifier combos, bracketed-paste markers, `SIGWINCH`. | Knowing anything about Visur. Producing Visur key tokens. |
| 2 | **Key adapter** | Map crossterm `KeyEvent` → **Visur key token** (the vocabulary `KeyPressed.from` accepts). Drops `Release` events, collapses `Repeat` to one token per repeat. Owns the paste → `PasteToken` translation. | Parsing escape sequences. Deciding what a key *means* in Visur. |
| 3 | **View shadow** | Hold the last `ViewUpdate` verbatim: viewport content, cursor, mode, submode, command state, change kind. Provide it to the screen model. | Interpreting it. Caching across resync boundaries. Growing a document. |
| 4 | **Screen model** | `Vec<Cell>` of `cols × rows`, `Cell { ch, fg, bg, attrs }`. Build the *next* grid from the shadow + terminal size, including line wrapping **if** the server does not wrap (open — see §6). | Any layout the protocol does not describe. Storing more than one screen. |
| 5 | **Compositor** | Diff previous grid against next grid; produce **damage as row runs** (never rectangles — the survey's finding). Own the two paint paths: `diff` and `full_repaint`. Force a full repaint on: resize, attach, alt-screen enter/exit, capability change. | Emitting bytes. Deciding when to paint (the loop does). |
| 6 | **Escape emitter** | Damage → bytes: `CUP` per dirty row, maximal same-attribute runs, `SGR` only on change, `EL` for blank tail, cursor placement plus `?25l/?25h`, `?1049` enter/exit, `?2026` brackets, one `write(2)` per frame. Holds the probed capability flags. | Being a TUI framework. Tracking what is on screen (the compositor owns that). |
| 7 | **Lifecycle + loop** | Raw mode, alt screen, panic/`SIGINT`/`SIGTERM` restore via a `Drop` guard, `SIGWINCH`. Single `poll`-driven event loop over {stdin, transport, signal}: update shadow → compose → damage → emit, coalescing anything that arrives before the frame flushes. | Blocking on the network. Rendering when nothing is dirty. |
| 8 | **Transport out** | Send key tokens and control messages (`resize`, `redraw`, `paste`). | Serialising beyond what the codec says. |
| 9 | **Transport** | Bytes both ways over a socket. Trait with implementations for TCP-JSON, unix-socket-JSON, or Vert.x event-bus framing. **Chosen in ticket 08.** | Knowing about views or keys. |
| 10 | **Protocol codec** | `ViewUpdate` ⇄ JSON (or the chosen framing), with a version field and tolerance for unknown fields so the server can grow the payload without breaking old clients. **Shape owned by ticket 03.** | Business logic. |
| 11 | **Launcher** (`visur`) | One command. Probe the server; spawn it if absent; wait for readiness; exec the client. See §5. | Being the client. |

### What the client owns versus crossterm

Crossterm is entry-level only: **raw mode, input parsing, `SIGWINCH`, terminal size, and (optionally, unused) escape `Command`s**. The client owns the cell grid, the damage model, the escape bytes, the paint policy, and the protocol. Nothing above component 1 is a library's decision, which is what keeps the spec's repaint half testable.

`ratatui` is deliberately **not** used. It is a widget/buffer layer that owns the buffer diff and the backend, i.e. components 4–6. Adopting it later is one `cargo add`, and this design is written so that it could be — but adopting it now would move the protocol's repaint contract into a dependency.

## 6. The server contract — how the client gets and modifies the view

### Modify

```
client                          server
  │ key token                      │
  ├──── keyWasPressed ────────────►│  KeyWasPressedVerticle.handle
  │                                │    → KeyPressed.from → Keymap → VisurCommand
  │                                │    → CommandExecutionService → operator
  │                                │    → editor model mutated
  │                                │  → modelWasChanged
  │                                │  ModelWasChangedVerticle.handleChange
  │                                │    → view produced from EditorModelCoupler
  │◄──── viewWasChanged ───────────┤    (shape: ticket 03)
  │                                │
  │ shadow ← update                │
  │ grid   ← compose               │
  │ damage ← diff                   │
  │ write(2)                        │
```

Two properties matter and both are ticket 03's to enforce:

- **The view message is a complete viewport state, not an edit.** The client never applies a patch, so it can never desync, and a dropped message costs one repaint rather than a corrupted screen.
- **The payload is viewport-bounded.** Never the document. The baseline ticket measured ~10 ms/MB for the full buffer; at 5 MB a keystroke costs 32–122 ms today, which is the entire vim-parity budget spent on bytes the client cannot see.

### Get (attach / resize / redraw)

```
client                                  server
  ├──── viewportWasChanged {cols,rows} ─►│  CanvasWasChangedVerticle equivalent
  │                                     │    → canvas dimensions stored in emc
  │                                     │    → view recomputed for the new size
  │◄──── viewWasChanged (full) ─────────┤  (forced full repaint path)
```

Today the browser sends `canvasWasChanged` with **cells derived from a pixel canvas** ([visur.js:110-120](../../../../src/main/resources/static/visur.js#L110-L120) — it divides by `cellWidth`/`cellHeight`). A terminal has `cols` and `rows` natively, so the terminal client sends them directly and the arithmetic disappears. Whether the event keeps its name or becomes `viewportWasChanged` is 08's business.

Today the browser also *forces* a redraw by echoing `modelWasChanged` back at the server ([visur.js:116-119](../../../../src/main/resources/static/visur.js#L116-L119)). In this design that becomes either an explicit `redrawRequested` message or nothing at all — the client can repaint from its shadow without a round trip, which is strictly better and worth stating in the spec.

### Ordering and resync

A single connection, messages applied in arrival order. Because every view message is a complete viewport, **order only matters for teleports**: a stale message just paints an older screen for one frame. Terminal resize is the one case that must invalidate the shadow (the server's viewport changed underneath it), and it does so by forcing a full repaint, not by patching.

## 5. The launcher (`visur`)

Requirement from the user: one command, which uses an already-running server if there is one, and starts one if there isn't.

```
visur
  ├─ probe 127.0.0.1:$VISUR_PORT (default 8888)
  │    ├─ reachable ──► do NOT own it; exec client; do not shut it down on exit
  │    └─ dead ───────► spawn server (see below), own it, wait for readiness
  │                       └─ on client exit: shut the server down we started
  └─ exec client (replaces this process)
```

Details that matter:

- **Readiness ≠ port open.** Gradle boots the JVM in a couple of seconds; the port appears before the editor model is initialised. Wait for a successful handshake (a `viewportWasChanged` that yields a `viewWasChanged`), not just a `connect`.
- **Spawn path.** Prefer the shadow/fat jar over `./gradlew run` (no Gradle daemon, no compile check, ~sub-second start); fall back to `./gradlew run` when the jar is missing or older than `src/`. Choosing which is a packaging decision that lands in the spec.
- **Ownership on exit.** A server the launcher started is killed when the client exits; a server it found is left alone. This is the choice that stops `visur` from becoming a rogue-process factory — and it needs a spec line, because the alternative (keep it warm for the next invocation) is defensible for latency.
- **One command means one command.** `./gradlew run` stays the answer for *starting the server alone*; `visur` is the answer for *using Visur*. Neither should require the other's knowledge.

## Per-stage latency budget

Derived from the baseline measurements, which are in [the closed ticket](../issues/01-baseline-measurements.md). Vim parity is ~1–2 ms; this is where it goes.

| Stage | Budget | Basis |
|---|---|---|
| key decode + adapter | ≤ 0.1 ms | one small parse per keystroke |
| transport out + in (loopback) | ≤ 1.0 ms | measured 1 ms for the outbound leg; server work was 0.30 ms at 8 KB |
| protocol decode (viewport-bounded) | ≤ 0.2 ms | 1.7 ms measured for 517 KB; a viewport is 3–6 KB |
| compose + diff | ≤ 0.5 ms | row-run diff over ~3.5 k cells |
| escape emit + `write(2)` | ≤ 0.3 ms | a few KB of SGR/CUP |
| **total** | **≤ 2.1 ms** | matches vim |

The whole budget is ~1/8 of the current 17.5 ms floor, which is why leaving the browser's SockJS path is what makes goal 2 possible at all.

## Open, and owned elsewhere

- **Transport and framing** — ticket 08. Everything above is written against a `Transport` trait so this stays swappable.
- **Payload shape, change kind, and whether the server pre-wraps lines** — ticket 03. Wrapping is the one genuine boundary hole in the diagram: today the *browser* wraps ([visur.js:167-171](../../../../src/main/resources/static/visur.js#L167-L171) — `x % canvasWidth`), so an "unwrapped viewport slice" would not be a viewport at all. Either the server sends wrapped rows and the terminal gets them free, or the terminal reimplements the canvas's wrapping rule exactly. **Recommend: server wraps**, because two renderers must not disagree about where a line ends.
- **Key token vocabulary (browser-shaped names vs redesign)** and the full byte→token table — ticket 06.
- **Parity split, `?2026` and truecolor probing, scroll model** — ticket 08.
