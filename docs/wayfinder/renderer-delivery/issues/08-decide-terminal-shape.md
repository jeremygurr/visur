# Decide the terminal client's shape, transport, and parity

Type: grilling
Status: open
Blocked by: 02, 03, 07

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
