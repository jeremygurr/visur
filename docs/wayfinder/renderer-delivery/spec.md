# Visur renderer + view protocol — specification

Status: **decided**. Produced by the closing ticket of the `renderer-delivery` map, 2026-10-28, from nine resolved tickets. Every input is cited; where this document asserts a number it came from a measurement recorded on a ticket.

An implementer should be able to build from this without asking a question. If one arises, it belongs on a new ticket, not in a silent decision here.

**Scope:** how Visur's editor state reaches a renderer, and what a second renderer (a terminal client) is.

**Not in scope:** implementing it (a separate effort), multi-user editing, filesystem navigation, any Visur feature that doesn't exist yet.

---

## 0. The two goals, and their different bars

| Goal | Bar | Why |
|---|---|---|
| Canvas updates stop scaling with document size | **bounded**, not vim parity | measured: paint is already 1.6–2.4 ms and viewport-bounded; only the payload scales |
| Visur opens from a terminal | **vim parity, ~1–2 ms** keystroke → last cell | reachable: a spike hit 2.6–4.4 ms with a naive renderer over the existing bus. **Measured 2026-11-02: 2.33–2.41 ms**, ~0.3 ms above the tendency and inside the 16 ms bar ([conformance](../terminal-build/conformance.md)) |

The measured baseline ([ticket 01](issues/01-baseline-measurements.md)): payload ≈ 6.5 KB and round trip 17.5 ms at the current 8 KB document; payload ≈ 5.29 MB and 52–122 ms at 5 MB; paint 1.6 ms at 1454×900 and 2.4 ms at 2546×1440, **flat across buffer sizes**. Payload costs ~10 ms/MB.

---

## 1. The view protocol

### 1.1 One message = one complete viewport state

Every `viewWasChanged` message is a **complete description of what a renderer should show**. It is never an edit, never a delta. Consequences that are part of the contract:

- A renderer never applies a patch, so it cannot desync.
- A dropped message costs one repaint, not a corrupted screen.
- Ordering matters only for viewport jumps; the newest message wins.
- The payload is bounded by the viewport, never by the document.

### 1.2 Exact shape

```jsonc
{
  "full": false,                    // bool.  true => discard all cached rows and repaint; do not diff
  "origin": 1200,                   // int.   absolute char index of rows[0].start; the viewport anchor
  "rows": [                         // array. one entry per SCREEN ROW, already wrapped by the server
    { "start": 1200, "text": "…" }, //  start: int, absolute char index of text[0]
    { "start": 1293, "text": "…" }  //  text:  string, no "\n" may appear in it
  ],
  "span": 1,                        // int.   quanta selected; 0 is none
  "cursorQuantumStart": 1240,       // int.   absolute char index
  "cursorQuantumEnd": 1241,         // int.   absolute char index
  "cursorRow": 3,                   // int.   index INTO rows — not a document row
  "cursorCol": 47,                  // int.   index into that row's chars
  "isAtQuantumStart": true,         // bool
  "cursorQuantum": "character",     // string name of the quantum
  "scopeQuantum": "character",      // string name of the search scope quantum
  "editorMode": "navigate",         // string
  "editorSubmode": "navigate",      // string
  "isInCommandState": false,        // bool
  "commandStateContent": "",        // string
  "commandCursor": 0,               // int
  "searchTarget": ""                // string. always present; "" when not searching
}
```

Field-by-field rationale is on [ticket 03](issues/03-decide-view-protocol.md). Three things to notice:

- **`rows` carries its own origins.** Any absolute index (`cursorQuantumStart/End`, future selections) maps to (row, col) by a scan of `rows`. No renderer owns a wrap table.
- **`cursorRow`/`cursorCol` are computed server-side.** Layout is not renderer work. `cursorRow` is an index into `rows`, may be `>= rows.length` when the cursor sits past the last character — renderers **clamp**, never crash.
- **`searchTarget` is unconditional.** Previously it appeared only in search submodes; a uniform shape means a renderer can hold a fixed struct.

### 1.3 Removed fields

| Was | Why gone |
|---|---|
| `editorContent` (whole document) | replaced by `rows` — the entire point |
| `ca` | assigned by the browser at [visur.js:50](../../../src/main/resources/static/visur.js#L50), read nowhere |
| `canvasEnd` | derivable from the last row (`start + text.length`) |

### 1.4 Renderer obligations

1. **MUST** treat every message as complete. **MUST** ignore unknown fields (forward compatibility).
2. **MUST** treat `full: true` as *discard cached rows, do not diff*.
3. **MUST NOT** re-wrap, re-order, or re-flow `rows`. Row boundaries are the server's word.
4. **MUST NOT** hold editor state, derive editor behaviour, or apply edits. See §6.
5. **MUST** clamp `cursorRow`/`cursorCol` into range rather than indexing out of bounds.
6. **MUST** paint a blank screen if `rows` is empty (empty document); do not fall back to a cached document.

---

## 2. Inbound: `viewportWasChanged`

A client declares the content grid it can paint.

```jsonc
{ "cols": 100, "rows": 30 }
```

- **Units are cells**, never pixels. Every renderer knows its own cell size; the browser divides before sending ([visur.js:110-113](../../../src/main/resources/static/visur.js#L110-L113)), a terminal has `cols`/`rows` natively.
- **`rows` excludes client chrome.** A terminal that paints its own mode line declares one fewer row.
- Server side: store the viewport, recompute, emit a `viewWasChanged` with `full: true`.

**Why one event and one handler with no branches.** Nothing in it is interface-specific, and a per-interface verticle would run identical code against identical global state. The rule for this class of question: **split when behaviour differs, not when callers differ.** ([ticket 03](issues/03-decide-view-protocol.md) §C.)

**Addresses are per-direction.** Inbound is `viewportWasChanged`; the payload stays `viewWasChanged`. They must not share a name, or a bridge would echo each client's size message back to every client.

**A view is published, not sent.** `viewWasChanged` is state addressed to every attached renderer, so the server
broadcasts it (`publish`). With a point-to-point `send` the bus picks exactly one consumer per message, and a second
renderer — or the ghost of one whose socket died without unregistering — swallows half the views with no error
anywhere. Found 2026-11-01 when the terminal client made a second consumer normal rather than hypothetical.

### 2.1 Frames on `/ws`

The terminal's socket is a pipe to the event bus, and nothing more. A frame is the bus envelope in both
directions — `{"address": …, "body": …}` — so the transport invents no vocabulary of its own:

```jsonc
// inbound
{"address": "viewportWasChanged", "body": {"cols": 100, "rows": 29}}
{"address": "keyWasPressed",      "body": {"key": "j"}}
// outbound
{"address": "viewWasChanged",     "body": { /* §1.2 */ }}
```

Plain JSON, no framing, no heartbeat, no session id: the socket is loopback-only and one client is attached at a
time. The SockJS bridge at `/eventbus` carries the identical messages for the browser, with its own framing.

---

## 3. The seam

Three pieces, one task each. This is the single-responsibility split: layout is not projection, and neither belongs in a verticle.

| Piece | One task | Knows about |
|---|---|---|
| **`Viewport`** (new, `com.ple.visur`) | **lay out** — `(content, width, origin, height)` ⇒ `rows` with origins | plain values only. **Not `EditorModelCoupler`** |
| **`View`** (replaces today's 2-field struct) | **project** — `View.of(emc)` reads state, delegates layout to `Viewport`, `render()` returns the payload | the `emc` getters it must read |
| **`ModelWasChangedVerticle`** | **pipe** — `bus.send(BusEvent.viewWasChanged.name(), View.of(emc).render())` | nothing |

`Viewport` being a pure function of plain values makes it the first genuinely unit-testable piece in this area: no `emc`, no event bus, no event loop.

### 3.1 Why `Viewport` can slice from an arbitrary origin

[`getCanvasEnd()`](../../../src/main/java/com/ple/visur/EditorModelCoupler.java#L91-L108) computes the visible region in wrapped rows, and [`decrementCanvasStart`](../../../src/main/java/com/ple/visur/EditorModelCoupler.java#L354-L379) keeps `canvasStart` walk-aligned to `canvasWidth` within a line. So re-slicing from `origin` at `canvasWidth` reproduces the browser's global `x % canvasWidth` wrap exactly, without walking from index 0. **`Viewport` does not depend on that invariant.** The invariant is what keeps rows stable across a scroll, but
`Viewport` itself is origin-agnostic: the browser's wrap is a pure function of (content, width, where painting
starts), so slicing from an arbitrary origin reproduces exactly what it drew — including after a resize, when
`canvasStart` stops being a multiple of the new width. A mid-row origin is therefore a tested case rather than a
violated precondition ([terminal-build ticket 02](../terminal-build/issues/02-extract-viewport-view.md)).

### 3.2 Class-by-class changes

| Class | Change |
|---|---|
| [`View`](../../../src/main/java/com/ple/visur/View.java) | no longer a mutable holder of `ca`/`editorContent`; becomes the payload producer (`of`, `render`) |
| **`Viewport`** | **new** — layout value type |
| [`ModelWasChangedVerticle`](../../../src/main/java/com/ple/visur/ModelWasChangedVerticle.java) | loses the `View view` **field** (shared mutable state across messages on a shared event loop) and `toJson()`; becomes a pipe |
| [`CanvasWasChangedVerticle`](../../../src/main/java/com/ple/visur/ViewportWasChangedVerticle.java) | renamed `ViewportWasChangedVerticle`; body becomes `putViewport` → `modelWasChanged`; **the debug loop at lines 25-33 is deleted** (it prints every line of the document on every resize) |
| [`BusEvent`](../../../src/main/java/com/ple/visur/BusEvent.java) | `canvasWasChanged` → `viewportWasChanged`; `viewWasChanged`, `keyWasPressed`, `modelWasChanged` unchanged; comments updated |
| [`MainVerticle`](../../../src/main/java/com/ple/visur/MainVerticle.java) | inbound permit renamed; `/ws` route added (§5.2) |
| [`EditorModelCoupler`](../../../src/main/java/com/ple/visur/EditorModelCoupler.java) | gains `getSearchPrompt()` (replacing the `"_"` concatenation in the serializer); `getCanvasStart/End` stay as scroll logic, **not** protocol fields |
| [`visur.js`](../../../src/main/resources/static/visur.js) | reads `rows`; **deletes its wrapping** ([visur.js:167-171](../../../src/main/resources/static/visur.js#L167-L171)); paints rows; cursor from `cursorRow`/`cursorCol` |

### 3.3 The content seam — one line, and what it is not

`View.of(emc)` reads the document in **exactly one named expression**, marked as the place a second content kind plugs in.

There is **no `ContentView` interface**, and that is a decision with a stated reason rather than a deferral: `emc.getEditorContent()` is called directly by sixteen operators and quantums ([InsertCharOp:10](../../../src/main/java/com/ple/visur/InsertCharOp.java#L10), [DeleteCursorQuantumOp:15](../../../src/main/java/com/ple/visur/DeleteCursorQuantumOp.java#L15), [ReplaceCharOp:8](../../../src/main/java/com/ple/visur/ReplaceCharOp.java#L8), [RelativeMoveOp:10](../../../src/main/java/com/ple/visur/RelativeMoveOp.java#L10), [SearchForwardOp:16](../../../src/main/java/com/ple/visur/SearchForwardOp.java#L16), [RegexQuantum](../../../src/main/java/com/ple/visur/RegexQuantum.java#L59) ×5, [LineQuantum:84](../../../src/main/java/com/ple/visur/LineQuantum.java#L84)), so an interface supplying a *different* content would produce a view no editing command could act on. **The seam is at `View`/`EditorModel`, not at content**; making it real is prerequisite P2 (§9).

---

## 4. The canvas renderer

### 4.1 What it stops doing

- **Stopping shipping the document.** `editorContent` is gone.
- **Stopping wrapping.** No `x % canvasWidth`, no `numberOfWrappedLines`. Row boundaries arrive in `rows`.
- **Stopping computing the cursor's cell** — `cursorRow`/`cursorCol` arrive.
- **Stopping the `modelWasChanged` echo** ([visur.js:116-119](../../../src/main/resources/static/visur.js#L116-L119)) used to force a redraw. `full: true` is the redraw signal; and a renderer that needs to repaint can repaint from its own last message without a round trip.

### 4.2 What it keeps

- **Full viewport repaint per update.** Paint is 1.6 ms at 1454×900 and 2.4 ms at 2546×1440, flat across document size. **Dirty-region rendering is explicitly not chartered** — it would buy ~1.5 ms and cost a compositor.
- Font choice, cell metrics (`20px Courier`), sub-cell cursor geometry (the `-0.5px` bar and the gold `fillRect` block), `innerHTML` mode line. These are reasons the canvas is *not* unified with the terminal (§5.6).

### 4.3 Budget

Keystroke → last painted pixel **≤ 16 ms at 5 MB**, since paint is ~2 ms and the payload is now viewport-bounded. Today's 5 MB figure is 52–122 ms; the change should land near the 8 KB figure of 32 ms, most of which is the SockJS floor and compositor wait rather than Visur's work.

**Measured 2026-11-02** ([conformance](../terminal-build/conformance.md) §1.2), in a real window at 100 KB / 1 MB / 5 MB: **15.0–15.8 ms**, flat across document size. Split in-page, the send→reply segment *is* the number at **14.8–15.5 ms** and the whole paint is **0.1–0.2 ms**, so the guess above was right: 98% of the budget is the bridge's frame, and what is left is Visur. The bar is met with about a millisecond to spare, and that margin belongs to Chrome's frame period rather than to this code. A headless window reports 51 ms against the same server — that is frame throttling, so any canvas number taken headless is measuring the browser.

---

## 5. The terminal client

### 5.1 Language and stack

**Rust, minimal tier** ([ticket 04](issues/04-decide-client-language.md)). `crossterm` is the *only* terminal dependency and it owns only the bottom edge:

| crossterm owns | the client owns |
|---|---|
| raw mode | the cell grid (`Vec<Cell>`) |
| stdin parsing → `Event` (ESC-vs-prefix timeout, Alt, modifiers, bracketed paste) | the damage model — row runs, **never rectangles** |
| `SIGWINCH`, terminal size | every escape byte emitted (`CUP`, `SGR`, `EL`, `?1049`, `?2026`, `?25`) |
| — | the paint policy (`diff` / `full_repaint`), the protocol codec, the key adapter |

`ratatui` is **not** used: it owns the buffer diff, the backend and text wrapping, i.e. exactly the components this spec defines. `tcell`/`bubbletea` (Go) for the same reason; Zig + `libvaxis` rejected on toolchain cost. A reference implementation of the thin path exists at [spike/](spike/README.md).

### 5.2 Transport

**Plain JSON over WebSocket on `ws://127.0.0.1:8888/ws`**, one message = one view update, one message = one key or control packet.

Server: ~10 lines in `MainVerticle` via Vert.x core's `HttpServerRequest.toWebSocket(...)` (verified present in vertx-core 4.4.4; `vertx-web` 4.4.4 has no `WebSocketHandler`). Handler responsibilities: on connect, register interest in `viewWasChanged`; on message, parse and dispatch to the inbound addresses; on `close`, unregister.

Client: any WebSocket implementation (the spike used `tungstenite`). Use WS ping/pong, **not** SockJS's heartbeat.
  **Corrected 2026-11-02:** the client sends no pings and the ping requirement is withdrawn. Over loopback there is no
  network to keep alive, and the event that matters — the server going away — arrives as a closed socket, which the
  client already turns into exit code `4` ([ticket 07](../terminal-build/issues/07-client-lifecycle.md)). A ping would
  add a timer and a false-negative mode ("the server is slow") to a transport with no latency to hide.

Bound to `127.0.0.1`. No auth, no TLS, no remote attach.

SockJS at `/eventbus` stays for the browser. It is **not** the terminal's transport: a SockJS client must reimplement `o`/`h`/`a[...]` framing plus a 5 s ping or the session is reaped at 10 s with `c[3000,"Go away!"]`.

### 5.3 Components

Eleven, one process, one thread, one `poll`-driven event loop. The client is a **stateless painter**. Full detail: [design/terminal-client-components.md](design/terminal-client-components.md).

| # | Component | Responsibility |
|---|---|---|
| 1 | key decoder (crossterm) | bytes → `Event`. **Its output type is `Event`, not `KeyEvent`** — paste is `Event::Paste(String)` |
| 2 | key adapter | `Event` → Visur key token, per §5.4; drops `Release`, forwards one token per auto-repeat |
| 3 | view shadow | holds the last `ViewUpdate` verbatim |
| 4 | screen model | `Vec<Cell>` of `cols × rows` built from shadow + `rows` |
| 5 | compositor | prev vs next grid → damage as row runs; two paint paths; force-full on resize |
| 6 | escape emitter | damage → bytes, one `write(2)` per frame, straight to fd 1 (not `std::io::Stdout`) |
| 7 | lifecycle + loop | raw mode, alt screen, ordered restore, `SIGWINCH`, **owns quit** |
| 8 | transport out | key and control packets |
| 9 | transport | the socket |
| 10 | protocol codec | payload ⇄ JSON, tolerant of unknown fields |
| 11 | launcher | one command (§5.7) |

### 5.4 Input: the complete token contract

`KeyPressed` is a string and `Keymap.get` is a hash lookup falling through to handlers. The full contract ([research](research/terminal-input-mapping.md) §1–2):

```
KeyToken := "Escape" | "Enter" | "Backspace"
          | "ArrowLeft" | "ArrowRight" | "ArrowUp" | "ArrowDown"
          | <any single-character string>
```

Nothing else is meaningful: a longer string misses every keymap and is silently dropped. The adapter's rules:

| Input | Token | Note |
|---|---|---|
| `KeyCode::Esc`, `mods == NONE` | `"Escape"` | the only ambiguous key |
| `KeyCode::Enter` | `"Enter"` | raw mode ⇒ `0d` |
| `KeyCode::Backspace` | `"Backspace"` | `7f` or `08` |
| `KeyCode::{Left,Right,Up,Down}` | `"ArrowLeft"` … | both `CSI` and `SS3` forms parsed by crossterm |
| `KeyCode::Char(c)`, `mods` without a chord | `c.to_string()` | the `F`/`N` uppercase bindings come through here |
| `KeyCode::Char(c)` with `CONTROL`/`ALT`/`SUPER`/`HYPER` | **drop** | otherwise `Ctrl-d` → `Char('d')` → fires navigate's `d` = `deleteCursorQuantum` |
| `KeyCode::Tab`, `Home`, `End`, `Insert`, `Delete`, `F(n)`, `PageUp`, `PageDown` | **drop** | unbound; keep the wire honest |
| `Event::Paste(s)` | **not a key** — see P3 | |
| `KeyEventKind::Release` | **drop** | Visur has only ever seen `keydown` |
| `KeyEventKind::Repeat` | **forward one token per repeat** | not one token per held key |

`SHIFT` must not be dropped. This line originally said "`SHIFT` alone needs no special handling: it already appears as the uppercase `Char`", and that is wrong in a way that costs every uppercase binding: crossterm reports `SHIFT` *alongside* `Char('Z')`, so a rule that drops every non-empty modifier set makes `F`, `N` and the `Z` prefix unreachable. Measured 2026-11-01 in a pty on macOS. The rule is "drop chords", not "drop modifiers": `CONTROL`/`ALT`/`SUPER`/`HYPER` are dropped, `SHIFT` is kept.

**The lone-`Esc` ambiguity.** crossterm resolves it with a private timeout; the exact constant was not verified and must be measured before it enters a test. The client should sidestep it: push kitty's `DISAMBIGUATE_ESCAPE_CODES` (`CSI >1u`), after which `Esc` arrives as the self-delimiting `CSI 27 u`. Treat that as best-effort — tmux traditionally swallows the push, which is why tmux users feel a ~50 ms `Esc`.

### 5.5 Output

- **Alternate screen (`?1049`)** on start, off on exit. This also removes the terminal's scrollback from the design: document scrolling is the app's job (§5.8).
- **`?2026` synchronized output**, *probed* with `DECRQM` (`CSI ? 2026 $ p`), never assumed, degrading silently. Terminal.app must be assumed to lack it.
- **Restore is an ordered sequence, not RAII** — crossterm does not restore on drop. A `panic::set_hook` plus a `Drop` guard must run `?2026l → ?25h → disable paste → pop keyboard flags → ?1049l → disable_raw_mode()`.
  **Corrected 2026-11-01 (ticket 07):** no `panic::set_hook` is needed. A `Drop` guard runs while the stack unwinds, which is the requirement; the hook was only ever a way to get there, and a hook that writes to a terminal mid-panic competes with the default one. The guard is idempotent so `main` can spend it before `process::exit`, which skips destructors. Paste is still unimplemented (P3), so its step is absent rather than skipped.
- **The capability answer cannot be read through crossterm.** `DECRQM`'s reply is not a key event, so crossterm's reader swallows it. The client reads fd 0 directly, before its first `poll`, with a 60 ms budget — and that window has two costs, both measured in ticket 07:
  - bytes typed inside the window are consumed with the answer (`shortcut` in `main.rs`);
  - **`SIGWINCH` is not yet being watched**, because crossterm installs its handler on the first `poll`, so a resize during the window is a signal nobody receives. The client therefore re-reads the terminal size after the probe and declares again if it moved.
- **One `write(2)` per frame** to fd 1, bypassing `std::io::Stdout`'s line buffering.
- Cursor style: block (inverse video) for any `span` above 0, bar for `span == 0`.
- **Frames are damage, not screens** (§5.3 components 5–6): a frame carries the rows that changed, as row runs, and ends with the cursor. Measured 2026-11-01 (ticket 06): a keystroke is 26 bytes against 3,234 for a full repaint at 100×30, and keystroke → paint through the real server fell from 5.91 ms to ~3.6 ms. A full repaint is neither the fallback nor the average case: it is `full: true`, the first frame of a session, and a geometry change.

### 5.6 What the terminal cannot have, deliberately

Canvas keeps what the terminal cannot express, and the spec does not try to unify them: font control and cell metrics ([visur.js:24-30](../../../src/main/resources/static/visur.js#L24-L30)), sub-cell geometry ([visur.js:179-208](../../../src/main/resources/static/visur.js#L179-L208)), arbitrary pixel colour, and a cheap full repaint. Conversely the terminal gets **zero-cost text layout**: rows arrive wrapped, so painting a row is a `write`, not 130 `fillText` calls.

### 5.7 Entry point

- `./gradlew run` starts the **server**. Unchanged, and it must stay the answer for that.
- The terminal client is started by the `visur` launcher: probe `127.0.0.1:8888`; if reachable, use it and leave it running on exit; if not, spawn it, own it, and shut it down on exit.
- ~~**Readiness is a handshake, not an open port.**~~ **Fixed at the source 2026-11-01.** The spike measured the
  first `viewportWasChanged` being lost regularly, because `deployVerticle` is asynchronous and the port opened
  before the bus consumers existed — a `send` to an address with no consumer is dropped silently. The server now
  opens its port only after the verticles are deployed, so one declaration is enough and the 300 ms resend is
  gone. A client that connects too early gets a refused connection, which is visible and retryable.
- **Packaging: the fat jar, when it is current.** The launcher prefers `build/libs/visur-*-fat.jar` (`java -jar`, no
  Gradle daemon, no compile check — about a second), and falls back to `./gradlew run` when the jar is missing or
  older than anything under `src/`, so a stale jar can never serve yesterday's editor. No binary is committed.
- **Exit codes carry detach vs quit.** `0` detach — leave the server running, even one the launcher started; anything
  else stops a server it started. A server it merely found is never its to stop. Implemented in the client (§5.9)
  and honoured by the launcher.
- **Readiness is the app's own URL.** The launcher probes `GET /static/visur.html`, not an open port, so something
  else squatting on 8888 is not mistaken for Visur.
- See [the launcher](../../../visur) and [its tests](../../../tests/launcher.sh).

### 5.8 Scrolling

**Server-mediated, follow-the-cursor.** A renderer holds only its viewport's rows and cannot scroll past them — it does not have the text. Moving past the edge is a **cursor move**; the server re-windows and answers `full: true`.

- A renderer that scrolls its own buffer is violating the protocol.
- **Not in beta:** page-scroll with a stationary cursor, and overscan/local scroll. Both need a new message or a bigger payload.

### 5.9 Parity

**Must** — paint rows and cursor (block for any `span` above 0, bar for `span` 0) from `cursorRow`/`cursorCol`; mode line with mode/submode/quantum/span; command-state line when `isInCommandState`; `h j k l`, arrows, `i`, `a`, `o`, `Esc`, printable, `Backspace`, `Enter`, `s`+digit; search (`f`/`F`/`n`/`N`/`/` and typing the target); `SIGWINCH` → `viewportWasChanged` → full repaint; `?2026` probe; alt screen, raw mode, ordered restore on panic **and** normal exit; **`ZZ` save-and-detach, `ZQ` detach, `ZD` quit**.

**Should** — kitty `DISAMBIGUATE_ESCAPE_CODES` push; `?2026` on every frame; overscan/local scroll; page-scroll without moving the cursor; truecolor once `VisurColor` is wired up.

**Won't** — mouse, panes, tabs, images; opening the `;` command line (no keybinding can — P4); search-and-replace (doesn't exist); file I/O (doesn't exist); per-client viewports (needs P1).

`ZZ`/`ZQ`/`ZD` is the *terminal's* binding, not a Visur command: raw mode makes `Ctrl-C` a byte, and **nothing in Visur quits** — `CommandStateService.exitCommandState` only leaves the command line. A `:q` equivalent waits for P4. Decided by the user 2026-11-01, replacing the earlier `Ctrl-C`/`Ctrl-Q` proposal, because in a shell those read as "kill this command".

- **`ZZ`** saves and detaches, **`ZQ`** detaches, **`ZD`** quits. *Detach* leaves the server running; *quit* asks for it to stop. `ZZ`'s save has nothing to save until file I/O exists (P5), so today it equals `ZQ` with intent — bound now so it starts working the day saving does.
- The distinction reaches the launcher as the **exit code**: `0` detach, `3` quit, `4` the server went away ([ticket 08](../terminal-build/issues/08-launcher.md)). `4` is not a decision the client made: any non-zero code tells the launcher to stop a server it started, and `4` says why the terminal came back.
- Consequence, recorded rather than hidden: `Ctrl-C` is dropped by the chord rule like `Ctrl-d`, so a wedged client is stopped with `SIGTERM` from outside, not from the keyboard.

---

## 6. The client boundary — a rule, not a preference

**No renderer holds editor state.** `EditorModelCoupler` is the only source of truth; renderers paint what they are told.

This was a standing preference; with the terminal client outside the JVM it is **structural**: a foreign process cannot reach `EditorModelCoupler`. The protocol enforces it in three ways: every message is complete (nothing to reconstruct), `rows` are pre-wrapped (nothing to lay out), and `cursorRow`/`cursorCol` are derived server-side (nothing to compute).

Testable form: *if a renderer can be deleted and restarted mid-session with only the next `viewWasChanged` message, it is conforming.*

---

## 7. Migration order

Each step is independently shippable; steps 2 and 3 pair so clients update once.

| # | Step | Intermediate state |
|---|---|---|
| 1 | Extract `Viewport` + `View` against the **current** payload | no wire change; the browser still receives `editorContent`; verify by running it |
| 2 | Switch the wire: add `rows`/`origin`/`full`/`cursorRow`/`cursorCol`; drop `editorContent`/`ca`/`canvasEnd`; **update `visur.js` in the same change** and delete its wrapping | both renderers see rows; the spike needs its layout replaced by `rows` in the same change, since it was wrapping for itself |
| 3 | Rename the inbound address to `viewportWasChanged`; add the `/ws` route; rename `CanvasWasChangedVerticle` → `ViewportWasChangedVerticle`; delete its debug loop | browser and terminal both on the new transport path |
| 4 | `emc.getSearchPrompt()`, deleting the `"_"` cursor hack | payload shape unchanged |

**No compatibility window.** There is one deployed client and it lives in this repo; dual-format would mean two server paths and a renderer that sniffs. The spike does break at step 2 — it painted `editorContent` and wrapped it itself — and it was fixed in that same change rather than waiting for step 3: consuming `rows` deleted its whole layout pass, so it came out smaller than it went in.

---

## 8. Non-goals

- Implementing any of this — the build is a separate effort.
- **Multi-user editing** (README feature 2). Requires P1.
- **Filesystem navigation** — `FileQuantum`, `DirectoryQuantum`, `NormalView`/`FilesystemView`. Requires P2. Shelved 2026-10-21; its notes survive in [todo](../../../todo).
- **Type-sensitive editing of objects** (README feature 1).
- **Remote / SSH attach**, TLS, auth.
- **A GUI client.** The protocol is renderer-neutral, which is what makes one possible; building it is not this spec.
- **Mouse, images, panes, tabs** in the terminal client.
- **Any plugin system.**
- **Visur feature gaps** found while charting: no quit, no copy/paste, no document-jump keys, no search-and-replace, no file I/O, of which only span has since closed ([span-and-count 04](../span-and-count/issues/04-setspanop-no-clamp.md)). These are the next effort's workload and its **benchmark set** — they are the operations that will expose the payload on large documents.

---

## 9. Prerequisites for later efforts

Recorded because they are load-bearing for anything that follows, and none of them is renderer work.

| # | Prerequisite | Blocks |
|---|---|---|
| **P1** | **Session-scoped viewport state.** *Confirmed empirically 2026-10-28: with the browser and the terminal open on the same document, the two renderers disagree about what is on screen and which was last.* `ServiceHolder.editorModelCoupler` is a **static singleton** and `canvasWidth`/`canvasHeight`/`canvasStart`/`fy` live inside it ([EditorModelKey](../../../src/main/java/com/ple/visur/EditorModelKey.java#L4-L35)), so two clients overwrite each other's viewport and both are rendered for the last caller. | multi-interface (`N` viewports), multi-user |
| **P2** | **Content-parameterise the operator/quantum stack** — sixteen `emc.getEditorContent()` call sites. | a second content kind, filesystem views, any `ContentView` seam |
| **P3** | **A paste message.** `Event::Paste(String)` is one string; forwarding it as N key events is N round trips. | bracketed paste, and every clipboard feature |
| **P4** | **Make command state reachable.** No keybinding sets `isInCommandState = true`; `CommandStateService` also lacks `ArrowUp`/`ArrowDown`. | the `;` command line, a `:q`-style quit, macros |
| **P5** | **File I/O.** Nothing is written to disk; "changes survive" means the server's memory survives. | saving, and any honest "quit" semantics |

## 10. Deferred rather than decided

**Nothing.** Every fog patch on the map graduated into a decision. Items that look deferred are prerequisites (§9) or non-goals (§8), and both are listed above rather than left implicit.
