# Conformance pass: the spec against the code

Produced by [ticket 09](issues/09-conformance-pass.md), 2026-11-02, on the build that closed tickets
[01](issues/01-benchmark-content.md)–[08](issues/08-launcher.md). Every number here came from
[`harness/`](../../../harness) on this machine, not from an earlier ticket:

```bash
./harness/sweep.sh                                   # the table, all three document sizes
./harness/target/release/visur-harness ws --cols 100 --rows 30   # wire + contract checks
./harness/target/release/visur-harness publish                   # one view, both renderers
```

The spec it measures: [renderer-delivery/spec.md](../renderer-delivery/spec.md).

---

## 1. Measurements

Median per keystroke, M2 Max, macOS 27.0.1. One fresh server per document size
(`VISUR_CONTENT_BYTES`), because the editor model is shared and outlives a client. Canvas in a **real window**
(`VISUR_HEADED=1`); see §1.3 for why that qualifier is load-bearing.

### 1.1 Both renderers

| Document | Renderer | Cells | Key → last cell | Paint | Payload |
|---|---|---|---|---|---|
| 100 KB | canvas | 124×41 | **15.0 ms** | 0.10 ms | 5,212 B |
| 100 KB | canvas | 196×56 | **15.8 ms** | 0.20 ms | 6,838 B |
| 100 KB | terminal | 100×30 | **2.33 ms** | included | 3,591 B |
| 100 KB | terminal | 250×70 | **2.36 ms** | included | 7,912 B |
| 1 MB | canvas | 124×41 | **15.7 ms** | 0.20 ms | 5,212 B |
| 1 MB | canvas | 196×56 | **15.1 ms** | 0.20 ms | 6,838 B |
| 1 MB | terminal | 100×30 | **2.39 ms** | included | 3,591 B |
| 1 MB | terminal | 250×70 | **2.41 ms** | included | 7,912 B |
| 5 MB | canvas | 124×41 | **15.5 ms** | 0.20 ms | 5,212 B |
| 5 MB | canvas | 196×56 | **15.3 ms** | 0.20 ms | 6,838 B |
| 5 MB | terminal | 100×30 | **2.36 ms** | included | 3,591 B |
| 5 MB | terminal | 250×70 | **2.34 ms** | included | 7,912 B |

Payload is flat from 100 KB to 5 MB to the byte at every viewport, and grows only with rows (≈113 B/row). Terminal
payload is the same message the canvas reads; the two columns differ only because the declared viewport differs.

### 1.2 Where the canvas's 15 ms goes

The browser number is almost entirely transport. Splitting it in-page:

| Segment | 100 KB | 5 MB |
|---|---|---|
| keydown → the page sends it | 0.00 ms | 0.00 ms |
| **send → the reply arrives on the socket (SockJS)** | **14.8–15.5 ms** | **14.9–15.3 ms** |
| socket arrival → the page's handler finished painting | 0.10–0.20 ms | 0.20 ms |

So Visur's own paint is 0.2 ms and the rest is one frame of the bridge. The spec guessed "most of which is the SockJS
floor and compositor wait rather than Visur's work" (§4.3) — measured, it is 98% of the number, and 15 ms is
essentially the compositor's frame period rather than a bridge cost that could be optimised away.

### 1.3 Headless is not a measurement environment

The same canvas run under `--headless=new` reports **51.3 ms**, of which 50.8 ms is "transport": a hidden window's
frames are throttled, so the reply waits for the throttler. The un-throttling flags for timers do not fix frame
pacing. Any future canvas number taken headless is measuring Chrome, not Visur — the harness therefore defaults the
browser to a real window and spells this out in its output.

---

## 2. Verdicts

Legend: **met** — verified, by machine where the ticket could. **met (unverified)** — implemented, no end-to-end
check. **not met** — the code does not do it. **not yet** — a prerequisite (§9) or non-goal blocks it. **by
decision** — the spec asks for something the build deliberately does not do, with the reason recorded here.

### §1 The view protocol

| Statement | Verdict | Evidence |
|---|---|---|
| 1.1 One message = one complete viewport | met | `paint::frame` / `visur.js` both consume a whole view; the harness sees 30–70 rows per message |
| 1.1 Newest message wins; a dropped one costs one repaint | met | client `take_view` coalesces to the newest |
| 1.1 Payload bounded by viewport, never the document | met | §1.1: flat to the byte across 50× the document |
| 1.2 Exactly the 17 fields of §1.2 | met | harness: `fields=17`, every frame |
| 1.2 No `"\n"` inside a row's text | met | harness: `newline_inside_a_row=false`; `Viewport.rows` consumes newlines |
| 1.2 `searchTarget` always present, `""` when not searching | met | field count; `View.searchTarget` returns `""` |
| 1.2 `cursorRow` may be ≥ `rows.length`; renderers clamp | met | `Viewport.position` clamps ([test](../../../src/test/java/com/ple/visur/ViewportTest.java)); both renderers clamp again |
| 1.4.1 Complete message; ignore unknown fields | met | `protocol::parse` tolerates unknown fields ([test](../../../terminal/src/protocol.rs)) |
| 1.4.2 `full: true` = discard cached rows, do not diff | met | `paint::frame` is the only place rows are discarded ([tests](../../../terminal/src/paint.rs)) |
| 1.4.3 Never re-wrap or re-flow `rows` | met | neither renderer measures text |
| 1.4.4 Never hold editor state | met | structural: the terminal is a separate process; the canvas holds only `view` |
| 1.4.5 Clamp the cursor rather than index out of bounds | met | both renderers clamp; the browser at [visur.js:112](../../../src/main/resources/static/visur.js#L112) |
| 1.4.6 Empty `rows` paints a blank screen | met | `compose` blanks the grid; the browser clears before painting |

### §2 Inbound

| Statement | Verdict | Evidence |
|---|---|---|
| Units are cells, not pixels | met | declared rows come back as rows: 45→45, 30→29, 70→69 |
| Declared `rows` excludes client chrome | met | terminal declares `rows - 1`; the double subtraction was removed in [ticket 04](issues/04-transport-and-renames.md) |
| Server stores the viewport and answers `full: true` | met | harness: `declaration_full=true keystrokes_full=never` |
| Addresses are per-direction | met | `viewportWasChanged` in, `viewWasChanged` out |
| A view is **published**, not sent | met | harness `publish`: two sockets, one keystroke, `after_one_keystroke=(true,true)` |
| §2.1 A `/ws` frame is the bus envelope both ways | met | required by the harness's own framing, and by both pty fake servers |
| §2.1 Plain JSON: no heartbeat, no session id, loopback only | met | `MainVerticle.isLoopback` (403 otherwise); no heartbeat in [transport.rs](../../../terminal/src/transport.rs) |

### §3 The seam

| Statement | Verdict | Evidence |
|---|---|---|
| `Viewport` is pure: plain values, no `emc`, no bus | met | [Viewport.java](../../../src/main/java/com/ple/visur/Viewport.java); 13 unit tests |
| `View` projects and serialises; `ModelWasChangedVerticle` is a pipe | met | [View.java](../../../src/main/java/com/ple/visur/View.java), [ModelWasChangedVerticle.java](../../../src/main/java/com/ple/visur/ModelWasChangedVerticle.java) |
| §3.1 A mid-row `origin` is a tested case | met | `startsAtAnOriginThatIsNotARowBoundary` |
| §3.2 Every class change listed | met | all eight applied; the debug loop is gone; `getSearchPrompt` exists ([EditorModelCoupler.java:151](../../../src/main/java/com/ple/visur/EditorModelCoupler.java#L151)) |
| §3.3 `View.of` reads the document in one named expression | met | one `getEditorContent()` call in `View.of`; no `ContentView` by decision |

### §4 The canvas renderer

| Statement | Verdict | Evidence |
|---|---|---|
| Stops shipping the document, wrapping, computing the cursor cell | met | `editorContent` gone; [visur.js:112](../../../src/main/resources/static/visur.js#L112) uses `cursorRow`/`cursorCol`; no `% canvasWidth` |
| Stops the `modelWasChanged` echo | met | the bridge permission for it was removed in [ticket 03](issues/03-switch-payload.md) |
| Keeps a full repaint per update (dirty regions not chartered) | met (by decision) | the browser still repaints in full; 0.2 ms of canvas work makes diffing pointless there |
| §4.3 Keystroke → last painted pixel ≤ 16 ms at 5 MB | **met, with no margin** | 15.0–15.8 ms at every size; 98% of it is the bridge's frame, 0.2 ms is Visur |

### §5 The terminal client

| Statement | Verdict | Evidence |
|---|---|---|
| §5.1 Rust, crossterm the only terminal dependency, no ratatui | met | deps: crossterm, serde_json, tungstenite, libc |
| §5.1 The client owns the grid, damage, escapes, paint policy | met | [screen.rs](../../../terminal/src/screen.rs), [paint.rs](../../../terminal/src/paint.rs) |
| §5.2 Plain JSON over `ws://127.0.0.1:8888/ws` | met | [transport.rs](../../../terminal/src/transport.rs) |
| §5.2 Server registers interest on connect, dispatches inbound, unregisters on close | met | close **and** exception handlers, added with `publish` in [ticket 05](issues/05-client-skeleton.md) |
| §5.2 Bound to `127.0.0.1`; no auth, no TLS | met | `isLoopback` |
| §5.2 "Use WS ping/pong, not SockJS's heartbeat" | **not met (by decision)** | the client sends no pings: the socket is loopback, and death is detected by `Closed` → exit 4 ([ticket 07](issues/07-client-lifecycle.md)). A ping would be a liveness timer for a network that is not there |
| §5.3 Eleven components, one thread, one loop | met (by decision) | eleven components in nine modules: components that never change independently share a file ([ticket 05](issues/05-client-skeleton.md)) |
| §5.4 Every row of the token table | met | [keys.rs](../../../terminal/src/keys.rs), 7 tests including the `SHIFT` correction |
| §5.4 `SHIFT` kept, chords dropped | met | measured in a pty; the rule is "drop chords" |
| §5.4 kitty `DISAMBIGUATE_ESCAPE_CODES` push | met | `caps::PUSH` at attach, popped in the restore |
| §5.5 Alternate screen on start, off on exit | met | pty test |
| §5.5 `?2026` probed with `DECRQM`, never assumed, degrading silently | met | [caps.rs](../../../terminal/src/caps.rs); pty tests for both answers |
| §5.5 Ordered restore, on panic and normal exit | met | [lifecycle.rs](../../../terminal/src/lifecycle.rs); order asserted; panic path proven at the guard seam, not end-to-end |
| §5.5 One `write(2)` per frame to fd 1 | met (by construction) | `ManuallyDrop<File>` on fd 1; no machine check counts syscalls |
| §5.5 Block cursor for `span == 1`, bar for `span == 0` | met | `DECSCUSR` in [paint.rs](../../../terminal/src/paint.rs) |
| §5.5 Frames are damage, row runs, never rectangles | met | §1.1 of [ticket 06](issues/06-client-damage.md): 26 B per keystroke vs 3,234 B |
| §5.6 Keeps what the canvas cannot express, and vice versa | met (by decision) | the terminal has no font/metric code; the canvas has no diff |
| §5.7 `./gradlew run` starts the server | met | unchanged |
| §5.7 Launcher probes, uses, or owns the server | met | 12 checks in [tests/launcher.sh](../../../tests/launcher.sh) |
| §5.7 Readiness is the app's own URL, not an open port | met | `GET /static/visur.html` |
| §5.7 Packaging: the fat jar when current, else Gradle | met | staleness test against `src/` |
| §5.7 Exit codes carry detach vs quit | met | `0` detach, `3` quit, `4` server gone |
| §5.8 Scrolling is server-mediated; the renderer never scrolls | met | the client has no scroll code and no text to scroll |
| §5.8 Page-scroll and overscan are not in beta | met (by decision) | not implemented |
| §5.9 Rows, cursor, mode line with mode/submode/quantum/span | met | [screen.rs](../../../terminal/src/screen.rs) |
| §5.9 Command-state line when `isInCommandState` | **met (unreachable)** | rendered and unit-tested; nothing sets `isInCommandState` — P4 |
| §5.9 `h j k l`, arrows, `i`, `a`, `o`, `Esc`, printable, `Backspace`, `Enter`, `s`+digit | met | directed through the adapter; `hjkl`/`i`/`Esc`/`s`+digit exercised end-to-end in the pty test |
| §5.9 `Backspace` on a line with no characters before it | met | the adapter sends `"Backspace"`; the *binding* is insert/replace only, as in vim |
| §5.9 Search: `f`/`F`/`n`/`N`/`/` and typing the target | met (partly unverified) | `f` + target exercised end-to-end; `F`, `n`, `N`, `/` reach the server as ordinary characters but no test drives them |
| §5.9 `SIGWINCH` → `viewportWasChanged` → full repaint | met | pty resize test asserts the declaration, the 40-row repaint, and the cursor's new bounds |
| §5.9 `ZZ` save-and-detach, `ZQ` detach, `ZD` quit | met | `zseq` unit tests; `ZQ` end-to-end; `ZD` through the launcher's code-3 path |
| §5.9 Should: `?2026` on every frame | met | when the probe said yes |
| §5.9 Should: truecolor | **not yet** | waits for `VisurColor`; the payload has no colour fields |
| §5.9 Won't: mouse, panes, tabs, images, search-and-replace, file I/O, per-client viewports | met | none implemented; P1–P5 open |

### §6–§10

| Statement | Verdict | Evidence |
|---|---|---|
| No renderer holds editor state | met | structural: separate process; the client's only cache is the grid it painted |
| Testable form: delete a renderer mid-session, restart it, the next view is enough | met | every pty check spawns a cold client and paints from one view |
| §7 No compatibility window; every migration step shipped | met | steps 1–4 all in the log, `getSearchPrompt` included |
| §8 Non-goals not accidentally implemented | met | none of them exist |
| §9 P1–P5 recorded and still open | met | P1 confirmed empirically in [renderer-delivery](../../wayfinder/renderer-delivery/map.md) |
| §10 Nothing deferred silently | met | the two `not met`/`not yet` rows above are prerequisites or recorded decisions |

---

## 3. Corrections made to the spec

1. **§5.2 "Use WS ping/pong"** — not implemented, and the reason is recorded in the spec: over loopback the socket's
   death is the event that matters, and it arrives as a closed socket, which the client already handles (exit 4). A
   ping adds a timer and a false-negative mode ("the server is slow") to a transport with no network in it.
2. **§4.3 budget** — the sentence "the change should land near the 8 KB figure of 32 ms, most of which is the SockJS
   floor" is now measured rather than inferred: 15.0–15.8 ms, of which 14.8–15.5 ms is the bridge and 0.2 ms is paint.
   The bar is met with about a millisecond to spare, and that margin is Chrome's frame period.
3. **§0's "~1–2 ms" terminal tendency** — measured 2.33–2.41 ms, release, from the pty harness's own write of the key
   to the frame arriving back. Within the ≤16 ms bar, ~0.3 ms above the aspirational tendency; the harness's
   write→frame boundary includes its own syscall, so treat 2.3 ms as an upper bound rather than a regression.

## 4. What was untried and is now tested

- **Resize during a search submode** — new pty check: `f`, resize to 120×40, then the search line is still there, 40
  rows are addressed, and the cursor is inside the new grid.
- **Document shorter than a screen** — covered by `Viewport` (`rows` returns fewer than `height`) and by the blank-row
  test in `compose`.
- **Document with no trailing newline** — the ordinary path; every `Viewport` test uses content without one.
- **Cursor at the very end of the buffer** — `positionClampsRatherThanIndexingOutOfBounds` (index 99 in a 7-char
  document), plus both renderers' own clamps.
