# Consolidate the spec

Type: task
Status: closed
Closed: 2026-10-28
Assignee: pi (agent session, 2026-10-28)

## Question

Write `spec.md` for both renderers, from the decisions recorded on [Decide the view-update protocol and where its seam sits](03-decide-view-protocol.md) (protocol and seam) and [Decide the terminal client's shape, transport, and parity](08-decide-terminal-shape.md) (client shape, transport, parity), with the key mapping from [Terminal input and output from an out-of-JVM client](06-terminal-io-non-jvm.md) and the language from [Decide the terminal client's implementation language and TUI stack](04-decide-client-language.md).

Nothing new is decided here — if a question opens while writing, it belongs on its own ticket, not silently resolved in the spec.

The spec must be sufficient that an implementer asks nothing further. Cover at least:

- **The view protocol** — exact JSON shape, field by field, with the change-kind discriminator and what a renderer may assume about ordering and resync
- **The seam** — the interface, its adapters, and which existing classes change: [`ModelWasChangedVerticle`](../../../../src/main/java/com/ple/visur/ModelWasChangedVerticle.java), [`View`](../../../../src/main/java/com/ple/visur/View.java), [`EditorModelCoupler`](../../../../src/main/java/com/ple/visur/EditorModelCoupler.java), [`BusEvent`](../../../../src/main/java/com/ple/visur/BusEvent.java), [`MainVerticle`](../../../../src/main/java/com/ple/visur/MainVerticle.java)
- **The canvas renderer** — what it stops doing (full buffer), what it keeps (full viewport repaint, unless the protocol ticket said otherwise), and the accepted latency budget from [Baseline measurements of the current update path](01-baseline-measurements.md)
- **The terminal client** — language and libraries, shape, transport and its wire format, startup and shutdown, the complete byte-sequence → key-token translation table, and the must/should/won't parity split
- **The client boundary** — the settled fact that no renderer holds editor state, stated as a testable rule the protocol enforces rather than a preference
- **The developer entry point** — how a developer starts server *and* client; `./gradlew run` must remain the answer, and the spec must say how
- **Migration order** — what lands first, and the intermediate states where both the old full-snapshot and the new protocol are in flight. A renderer written against the old snapshot must not break silently.
- **Non-goals** — pulled from the map's Out of scope, including that this spec is a design artifact and its implementation is a separate effort

Also note open items that were deferred rather than decided (the surviving patches in the map's Not yet specified), so the implementer knows what is deliberately unfinished and where the next effort starts.

Written to `docs/wayfinder/renderer-delivery/spec.md` and linked from the map. Closing this ticket closes the map.

Where to run this: with [Decide the terminal client's implementation language and TUI stack](04-decide-client-language.md) and [Decide the terminal client's process boundary and transport](05-decide-process-boundary.md) both resolved, this ticket is AFK-able — one agent, all inputs written down. Prefer that; the spec should not need a human in the room.

## Answer

Written to **[spec.md](../spec.md)** — ten sections, from the nine resolved tickets. Nothing new was decided while writing it; four consistencies had to be made explicit because two tickets had each left the same thing half-stated:

1. **Addresses are per-direction.** [03](03-decide-view-protocol.md)'s migration step said the event rename went "`viewportWasChanged` both ways", which would have made a bridge echo each client's size message back to every client. The spec fixes it: inbound `viewportWasChanged` (size), outbound `viewWasChanged` (payload). Ticket 03 carries the correction note.
2. **`Viewport` assumes `origin` is wrap-aligned** — a line start or `lineStart + k*width`. That follows from [`decrementCanvasStart`](../../../../src/main/java/com/ple/visur/EditorModelCoupler.java#L354-L379), but it is an invariant the implementer must not break, so it is stated in §3.1 rather than left as an inference.
3. **`rows` may be empty and `cursorRow` may exceed `rows.length`** — neither ticket said what a renderer does at the boundary. The spec says clamp, paint blank, never crash.
4. **The declared `rows` excludes client chrome** — the mode line is not part of the grid a client declares, which is what made the spike subtract one.

Deliberately *not* in the spec: anything deferred. The map's fog emptied before this ticket, so §10 says "nothing" and the items that remain are prerequisites (§9, five of them) or non-goals (§8) — listed rather than left implicit, so the next effort starts from a known state.
