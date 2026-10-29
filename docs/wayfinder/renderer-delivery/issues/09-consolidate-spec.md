# Consolidate the spec

Type: task
Status: open
Blocked by: 03, 08

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
