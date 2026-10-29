# Decide the terminal client's process boundary and transport

Type: grilling
Status: closed
Closed: 2026-10-28

## Question

Is the terminal client a piece of Visur's own JVM, a separate process on the same machine, or something the server renders bytes for?

Three coherent answers:

- **In the JVM** — a `vertx-shell` command (already a dependency) or a JLine/Lanterna `main` that reaches `EditorModelCoupler` directly. Cheapest to build, one process, no protocol needed at all for the terminal path.
- **Separate process, outside the JVM** — a client binary that speaks to the server only over the network. Every renderer then consumes the same protocol or it does not render.
- **Dumb pipe** — the server emits escape sequences and a shell forwards bytes to the TTY. Thinnest client, but couples the server to a terminal and strands the browser.

The choice is load-bearing for the whole map: an in-JVM client makes the "one protocol, two renderers" claim untestable, because the terminal path can bypass the protocol and read the model directly, and nothing would fail.

## Answer

**Separate process, outside the JVM.** Decided 2026-10-28 with the user, before the map's ticket work began; recorded here so it has one home.

The reasoning that killed the in-JVM option: the map's destination is a *renderer-neutral* protocol. A Java client sitting in the same process can quietly shortcut it — read `EditorModelCoupler`, call `View` directly, never touch the wire — and the spec would look satisfied while the protocol stayed unproven. That failure is silent, which makes it worse than the toolchain cost. This also turns the map's standing preference ("no editor state in a renderer") from a discipline into an architectural fact: an out-of-JVM client *cannot* reach editor state.

The dumb-pipe option was rejected for the obvious reason — it makes the canvas renderer and the terminal renderer diverge permanently and leaves nothing to specify that both must honour.

**Explicitly not decided here: the transport.** The `vertx-tcp-eventbus-bridge` dependency ([build.gradle.kts:30-34](../../../../build.gradle.kts#L30-34)) makes "reuse the Vert.x event-bus wire format" available, but a plain JSON-over-socket endpoint is available too, and which is right depends on the payload shape. That belongs to [Decide the terminal client's shape, transport, and parity](08-decide-terminal-shape.md), informed by [Decide the view-update protocol and where its seam sits](03-decide-view-protocol.md).

Consequences now sitting on other tickets: the client's language is open ([Decide the terminal client's implementation language and TUI stack](04-decide-client-language.md)); the key vocabulary must cross a process boundary, which the browser never had to do; and `./gradlew run` must still be the one command that starts everything.
