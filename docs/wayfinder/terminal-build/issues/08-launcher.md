# The `visur` launcher and packaging

Type: task
Status: closed
Closed: 2026-11-01
Blocked by: 05

## Work

Spec §5.7. One command that uses a running server or starts one, and shuts down only what it started.

- Probe `127.0.0.1:8888`; reachable → use it, leave it; dead → spawn, own, kill on exit.
- ~~**Readiness is a handshake, not an open port.**~~ Already fixed at the source: the server now opens its port only
after the verticles deploy, so the launcher probes the app's own URL (`GET /static/visur.html`) and needs no
message-retry loop. An open port is still not proof of Visur — something else can squat on 8888.
- Decide packaging: prebuilt fat jar (fast start, needs building) versus `./gradlew run` (slow, always current). `./gradlew run` must remain the answer for starting the server alone.

The spike's [launcher](../../renderer-delivery/spike/visur) is the starting point and already implements the probe/own/kill rule.

## Done when

- `visur` works from a cold machine state (no server) and from a warm one (server already up), and in the warm case leaves the server running on exit.
- A failed server start produces a useful message and no orphaned JVM.
- The chosen packaging is written down in the repo README or the spec, not just in the script.

## Result

`./visur` at the repo root, plus `./tests/launcher.sh` for the lifecycle.

```text
visur
 ├─ probe GET /static/visur.html on 127.0.0.1:8888
 │    ├─ 200 ──► "using the server already on :8888" ──► never ours to stop
 │    └─ else ─► start it (fat jar if current, else ./gradlew run), own it
 │                 ├─ ready ──► run the client
 │                 └─ died  ──► tail of the server log, exit 1, nothing left behind
 └─ client exits
      ├─ 0 (detach, ZZ/ZQ) ──► leave the server running, hand it over
      └─ anything else ─────► stop the server we started
```

**Packaging decided: the fat jar when it is current**, falling back to `./gradlew run`. The jar needs no Gradle
daemon and no compile check — about a second against several — and the "newer than every file under `src/`" test
means a stale jar can never serve yesterday's editor. Written down in the [repo README](../../../../README.adoc) and
in [spec §5.7](../../renderer-delivery/spec.md).

**Readiness is the app's own URL, not an open port.** Probing `/static/visur.html` is both the readiness check and
the identity check: something else squatting on 8888 fails it, where a TCP connect would have passed. With the port
now opening only after the verticles deploy, no message-retry loop is needed — the ticket's original bullet is stale
and corrected above.

### A bug this ticket found in the build

`java -jar build/libs/visur-*-fat.jar` printed Vert.x's usage instead of starting: the shadow manifest said
`MainVerticle-Verticle` where [the Launcher reads `Main-Verticle`](../../../../build.gradle.kts#L78-L83). The jar had
never been runnable; nobody had run it, because `./gradlew run` passed the verticle name on the command line and hid
the mistake. Fixed in `build.gradle.kts`, so `java -jar` alone now starts the server.

### Verified

- **`./tests/launcher.sh` — 12 checks, all green**: cold start with a quitting client stops the server it started;
  cold start with a detaching client leaves it running; a warm start reuses a server and survives a quitting client;
  a server that dies before answering produces the log tail and an exit code instead of a hang, with no orphaned JVM.
- **The real client, from a cold machine**, via the pty test with `VISUR_WRAPPER=./visur`: no server listening → the
  launcher started one → the client attached, typed, searched and detached with `ZQ` in **1.35 s total**, exit 0, and
  the server was left running as detach promises.
- The client's own path is unchanged and still passes with a server started by hand.

### Notes

- `VISUR_SERVER` and `VISUR_CLIENT` override how the server is started and which client runs. That is what makes the
  lifecycle testable without a terminal, and it is also the honest escape hatch for "use a server I started myself".
  An `nc` squatter on 8888 was tried first and rejected: Java does not reliably exit when the bind fails, so the
  scenario was timing-dependent rather than deterministic.
- Tests are bash, in `tests/launcher.sh`, so the repo stays Java/Rust/bash.
