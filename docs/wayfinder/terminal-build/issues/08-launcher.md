# The `visur` launcher and packaging

Type: task
Status: open
Blocked by: 05

## Work

Spec §5.7. One command that uses a running server or starts one, and shuts down only what it started.

- Probe `127.0.0.1:8888`; reachable → use it, leave it; dead → spawn, own, kill on exit.
- **Readiness is a handshake, not an open port.** Gradle's JVM opens the port before the editor model exists; resend the viewport declaration until the first `viewWasChanged` arrives. The spike measured this failing regularly.
- Decide packaging: prebuilt fat jar (fast start, needs building) versus `./gradlew run` (slow, always current). `./gradlew run` must remain the answer for starting the server alone.

The spike's [launcher](../../renderer-delivery/spike/visur) is the starting point and already implements the probe/own/kill rule.

## Done when

- `visur` works from a cold machine state (no server) and from a warm one (server already up), and in the warm case leaves the server running on exit.
- A failed server start produces a useful message and no orphaned JVM.
- The chosen packaging is written down in the repo README or the spec, not just in the script.
