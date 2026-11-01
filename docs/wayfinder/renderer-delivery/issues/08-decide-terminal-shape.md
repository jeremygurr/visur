# Decide the terminal client's shape, transport, and parity

Type: grilling
Status: closed
Closed: 2026-10-28
Assignee: pi (agent session, 2026-10-28)

## Question

What is the terminal client, how does it reach the server, and how much of Visur must it reproduce for beta?

The process boundary is already settled — the client is a separate, non-JVM process ([Decide the terminal client's process boundary and transport](05-decide-process-boundary.md)). What follows from that is not.

**A. Shape.** Two coherent designs, and they lead to different amounts of duplicated work:

- **Structured-payload client** — the client consumes the same view protocol as the canvas renderer (the output of [Decide the view-update protocol and where its seam sits](03-decide-view-protocol.md)) and decides how to paint it. One protocol, two renderers. Escape-sequence knowledge lives in the client.
- **ANSI pipe** — the server emits escape sequences and the client is a dumb pipe forwarding bytes to the TTY. Thinnest conceivable client, and now no longer absurd: with the client outside the JVM, the pipe costs almost nothing to write. But it couples the server to a terminal, strands the browser's structured content, and makes "renderer-neutral" a claim about a protocol only one renderer consumes.

**B. Transport.** The browser reaches the server over SockJS at `/eventbus` ([`MainVerticle.java`](../../../../src/main/java/com/ple/visur/MainVerticle.java)). Candidates, now weighted by the fact that the client is in the language decided in [Decide the terminal client's implementation language and TUI stack](04-decide-client-language.md):

- **Vert.x TCP event-bus bridge** — `vertx-tcp-eventbus-bridge` is already a dependency ([build.gradle.kts:30-34](../../../../build.gradle.kts#L30-34)) and unused. Free on the server; the client must reimplement the binary frame format (length-prefixed, `\0`-delimited frames) and the ping/pong heartbeat, in a language with no existing client library. Also exposes a port on the event bus, which has security and lifetime consequences.
- **New plain JSON over WebSocket** — one more listener in `MainVerticle`, a JSON codec on both ends, no frame protocol to reverse-engineer. Cheapest to make correct, adds server surface.
- **Plain JSON over TCP or a Unix domain socket** — same as above with less ceremony; a Unix socket additionally keeps the client off the network and gives you peer credentials for free. macOS-only scope makes this less of a portability problem than it looks.
- **SockJS** — do not. It exists to be the browser's fallback.

**Already decided above you — do not reopen:** the payload is viewport-bounded server-wrapped rows with per-row origins, one format for all renderers, with `N` viewports to come ([Decide the view-update protocol and where its seam sits](03-decide-view-protocol.md)). The spike argued for a structured-payload client, not an ANSI pipe ([Kilo-shaped spike: Visur in a terminal](07-kilo-spike.md)). Your remaining transport question is therefore narrower than it looks: the payload is JSON-shaped and self-describing, so the candidates in §B differ only in framing, connection lifetime and who pays for the wire format.

**Inherited from [Terminal input and output from an out-of-JVM client](06-terminal-io-non-jvm.md)**, which decided the input vocabulary but deliberately left these to you:

- **Paste.** `Event::Paste(String)` is not a key event. Decide whether a paste is one synthetic `insertChar`-shaped command stream or a dedicated message — the research favours a dedicated message, because a stream is one round trip per character.
- **Quit.** Raw mode clears `ISIG`, so Ctrl-C is a byte, and nothing in Visur quits. The client must bind an explicit quit/detach action; name it in the parity list or the client cannot be exited except by closing the terminal.
- **Command state is unreachable today.** No keybinding anywhere sets `isInCommandState = true` (`CommandStateService` also lacks Up/Down), so the `;` command line cannot be opened by any keypress. Either the client binds it — a server-side change this map should not smuggle in silently — or parity explicitly excludes it.
- **Capability probe at attach**: `?2026` via `DECRQM` and kitty `DISAMBIGUATE_ESCAPE_CODES` via `CSI >1u`, probe-not-version-check, degrading silently. The probe is what keeps the `Esc` key off the timeout path.

Weigh all of it against the standing preference that all state is server-side, and against the §A answer: an ANSI-pipe design wants a dumb byte transport, a structured-payload design wants a message transport.

**This ticket now carries a number, not a preference.** [Baseline measurements of the current update path](01-baseline-measurements.md) found a payload-independent **~17.5 ms round-trip floor** on the current SockJS path that is neither server work (0.3 ms) nor parse (0.1 ms) nor paint (1.6 ms), and it sits on the view-delivery leg. Vim is ~1–2 ms. So:

- **First task, before choosing**: isolate that floor — raw WebSocket echo vs the SockJS bridge, browser-side vs server-side. If it is the browser's SockJS delivery path, the canvas client can never reach goal 1's latency and the terminal client becomes the *only* path to it, which is a different map. If it is the SockJS bridge's framing, any transport in the candidate list fixes it.
- **Then choose against the terminal's budget**: keystroke → last cell painted **≤ 16 ms at 5 MB, trending toward vim's 1–2 ms** — the terminal bar is vim parity, not merely "bounded" (the canvas bar is the softer one; see the map's Destination). The payload is viewport-bounded by [Decide the view-update protocol and where its seam sits](03-decide-view-protocol.md), so what is left to measure is transport latency per keystroke. State in the answer which candidate hits the budget and how you know.

**C. Parity.** What must the terminal renderer do for beta, versus what can wait? Split explicitly into:

- **Must** — the modes and submodes a user needs for real editing ([`EditorMode`](../../../../src/main/java/com/ple/visur/EditorMode.java), [`EditorSubmode`](../../../../src/main/java/com/ple/visur/EditorSubmode.java)), command state (`isInCommandState`, `commandStateContent`, `commandCursor`), search prompt (`searchTarget`), span/cursor-quantum highlight, scrolling, resize
- **Should** — bracketed paste, truecolor fidelity of [`VisurColor`](../../../../src/main/java/com/ple/visur/VisurColor.java)
- **Won't** — mouse, images, multiple panes, tabs

Bring [Survey of incremental rendering and terminal capability baselines](02-terminal-render-survey.md)'s capability findings and [Kilo-shaped spike: Visur in a terminal](07-kilo-spike.md)'s verdict. The spike should already be arguing for one shape and one stand-in transport; confirm or overrule it, and record whichever the spike got wrong as evidence that the spike is a spike.

The answer records: chosen shape, chosen transport with its wire format sketched, the must/should/won't parity split, and what the canvas renderer keeps that the terminal cannot have — enough for the spec ticket to write both without reopening.

## Answer

Decided with the user 2026-10-28, on top of [the protocol](03-decide-view-protocol.md) and [the spike](07-kilo-spike.md).

### A. Shape: structured-payload client. ANSI pipe overruled.

The client consumes the view payload as **data** and owns its own layout and painting. The ANSI-pipe option — server emits escape sequences, client forwards bytes — is formally overruled, for three reasons that only became decisive once the payload was settled: the payload is viewport-bounded and self-describing, so it is only meaningful to a client that reads fields; an ANSI pipe would put terminal knowledge inside the server and leave the canvas renderer with nothing to consume; and it would make `N` viewports impossible rather than additive.

### B. Transport: plain JSON over WebSocket, on a new `/ws` route, localhost only

- ~10 lines in [`MainVerticle`](../../../../src/main/java/com/ple/visur/MainVerticle.java) using Vert.x core's `HttpServerRequest.toWebSocket(...)` — verified present in vertx-core 4.4.4, and `vertx-web` 4.4.4 has no `WebSocketHandler`, so this is the route that needs no new dependency.
- **One WebSocket message = one view update**, same JSON shape as decided in [03](03-decide-view-protocol.md). Keys go the other way as `{"key": token}`. Standard WS ping/pong replaces the hand-rolled SockJS heartbeat.
- Bound to `127.0.0.1`. No auth, no TLS, no remote attach — remote is out of scope on the map.

**Rejected, and why:**

- **SockJS (works today, zero server change)** — the client must reimplement `o`/`h`/`a[...]` framing *plus* a 5 s ping or the session is reaped at 10 s with `c[3000,"Go away!"]` (spike finding). That is a browser-shaped protocol maintained forever in Rust, for a payload that is now our own JSON. Speed was never the objection: 2.6–4.4 ms either way.
- **`vertx-tcp-eventbus-bridge`** — free server-side, but the client implements Vert.x binary framing and heartbeats in a language with no client library.
- **Plain TCP / Unix domain socket** — *not* rejected on merit; kept as a `Transport` implementation behind the same trait if a second listener ever earns its keep. Domain-socket support in Vert.x was not verified, so claiming it would be dishonest.

Because the client speaks a `Transport` trait, this choice is one small file to reverse. That is the real reason it is safe to decide now.

### C. Scrolling: server-mediated, follow-the-cursor, for beta

A renderer receives only its viewport's rows, so **it cannot scroll past them** — it does not have the text. Consequences, and these go in the spec as obligations:

- Moving past the edge is a **cursor move**; the server re-windows and sends `full: true`.
- A renderer that "scrolls its own buffer" is violating the protocol. The rows it holds are the only truth.
- **Not in beta**: page-scroll with a stationary cursor (`Ctrl-E`-style), and overscan/local scroll. Both recorded as *should*; both need either a new message or a bigger payload.

### D. Parity split

**Must** — paint rows and cursor (block for `span` 1, bar for `span` 0) from `cursorRow`/`cursorCol`; mode line with mode/submode/quantum/span; command-state line when `isInCommandState`; `h j k l`, arrows, `i`, `a`, `o`, `Esc`, printable, `Backspace`, `Enter`, `s`+digit; search (`f`/`F`/`n`/`N`/`/` and typing the target); `SIGWINCH` → `viewportWasChanged` → full repaint; capability probe for `?2026` via `DECRQM`, degrading silently; alt screen, raw mode, ordered restore on panic *and* normal exit; **quit on `Ctrl-C` and `Ctrl-Q`**.

**Should** — kitty `DISAMBIGUATE_ESCAPE_CODES` push, degrading gracefully when tmux swallows it; `?2026` brackets on every frame; overscan/local scroll; page-scroll without moving the cursor; truecolor once `VisurColor` is wired up.

**Won't** — mouse reporting, panes, tabs, images; opening the `;` command line (no keybinding can, server-side — see [06](06-terminal-io-non-jvm.md)); search-and-replace (does not exist); any file I/O (does not exist); per-client viewports (needs session scoping).

`Ctrl-C`/`Ctrl-Q` = quit/detach is the terminal's own binding, not a Visur command; a `:q` equivalent waits until command state is reachable.

### E. What the canvas renderer keeps that the terminal cannot have

Recorded so the spec doesn't try to unify them:

- **Font control.** The canvas measures `20px Courier` and derives its own cell metrics ([visur.js:24-30](../../../../src/main/resources/static/visur.js#L24-L30)); a terminal's cell size is the terminal's, and the client only asks for `cols`/`rows`.
- **Sub-cell geometry** — the cursor bar is drawn at `x * cellWidth - 0.5`, and the span block at `+5px` offsets ([visur.js:179-208](../../../../src/main/resources/static/visur.js#L179-L208)). A terminal has one cell, no fractions.
- **Color-by-construction.** The canvas can fill any pixel any colour; the terminal gets `SGR` and whatever the terminal supports, which is why truecolor and `?2026` probing are terminal-only concerns.
- **Arbitrary redraw cost.** Canvas paint is measured at 1.6–2.4 ms and viewport-bounded, so the canvas can afford the naive full repaint; the terminal must diff rows to stay inside its `~2 ms` budget at large viewports.
- Conversely the terminal gets something the canvas cannot have: **zero-cost text layout**. Rows arrive wrapped, so painting a row is a `write`, not 130 `fillText` calls.

### F. Recorded prerequisites (not client work)

- **Bracketed paste needs a server message.** `Event::Paste(String)` arrives as one string; forwarding it as N key events is N round trips. This is a server-side addition, recorded with the others rather than smuggled into the client.
- **Session-scoped viewport state** — from [03](03-decide-view-protocol.md), unchanged: `N` viewports and multi-user both need it.
- **Command-line reachability** — from [06](06-terminal-io-non-jvm.md), unchanged: nothing sets `isInCommandState = true`.

### G. Latency note inherited from [06](06-terminal-io-non-jvm.md)

Under `tmux`, `SIGWINCH` is pane-shaped and one hop late, `?2026` is unavailable on tmux ≤ 3.4, and the kitty push is traditionally swallowed — so a tmux user gets the ESC-timeout `Esc` and no synchronized output. Worth one line in the spec so it is a known limitation rather than a bug report.
