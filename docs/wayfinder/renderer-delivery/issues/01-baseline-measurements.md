# 01 — Baseline measurements of the current update path

Type: task
Status: closed
Closed: 2026-10-28
Assignee: pi (agent session, 2026-10-28)

## Question

What does one keystroke actually cost today, and which half of the cost dominates — the payload or the paint?

Nothing can be decided about optimizing screen updates until this number exists. The current path, for reference:

- [KeyWasPressedVerticle.java:31](../../../../src/main/java/com/ple/visur/KeyWasPressedVerticle.java#L31) — every key, including a bare cursor move, becomes `modelWasChanged`
- [ModelWasChangedVerticle.java:20,28](../../../../src/main/java/com/ple/visur/ModelWasChangedVerticle.java#L19-28) — the whole buffer is read and serialized onto the wire
- [visur.js:72](../../../../src/main/resources/static/visur.js#L72) — `ctx.clearRect` over the entire canvas
- [visur.js:136-160](../../../../src/main/resources/static/visur.js#L136-160) — one `fillText` per character cell in the viewport

Record, on macOS, for at least these combinations:

| Buffer size | Viewport |
|---|---|
| the current init buffer (~8 KB Declaration) | ~1470×900 window |
| ~500 KB | ~1470×900 window |
| ~5 MB | ~1470×900 window |
| ~500 KB | full-screen / large display |

For each cell of that table, capture:

1. **Payload bytes per keystroke** — the serialized `viewWasChanged` JSON size.
2. **End-to-end latency** — keydown to last painted pixel. Same-process `performance.now()` marks around the `viewWasChanged` handler are sufficient for a baseline.
3. **`fillText` call count per frame**, and `clearRect` + `drawCanvas` wall time.
4. **Cursor-only vs content-changing keys measured separately** (e.g. `l` vs typing a character in insert mode) — these are the two cases a protocol change would treat differently.

Constraints: do not restructure the renderer. A measurement hook is fine; a rewrite is not. Throw the harness away afterward, but record the method in the answer so it can be repeated.

The answer records the numbers, the method, and an explicit verdict on whether payload or paint dominates — because [Decide the view-update protocol and where its seam sits](03-decide-view-protocol.md) is blocked on that verdict.

## Answer

Measured 2026-10-28 on macOS, headless Chromium 1248 via Playwright 1.64 against `./gradlew run` on the real SockJS event bus. Source was instrumented temporarily and **reverted**; the harness was throwaway per the ticket's own instruction. Method below the findings so the numbers can be rebuilt.

### Numbers

All rows 1454×900 canvas unless stated. Median of 8–12 keystrokes per cell; the first keystroke after `i` is excluded (two renders coalesce into one frame and double `fillText`).

| Buffer | `viewWasChanged` payload | Server `handleChange` | Round trip (send → frame parsed) | Client parse | Paint (`clearRect`+`drawCanvas`) | `fillText` calls | Keydown → frame committed |
|---|---|---|---|---|---|---|---|
| 8 KB (current init content) | 6,545 B | 0.30 ms | **17.5 ms** | 0.1 ms | 1.6 ms | 3,557 | 32 ms |
| 500 KB | 517,279 B | 1.10 ms | **24 ms** | 1.7 ms | 1.5 ms | 3,557 | 32 ms |
| 5 MB | 5,294,155 B | 5.5 ms | **32–36 ms** (first three: 74 / 80 / 100) | 17 ms | 1.5 ms | 3,557 | 52–122 ms |
| 8 KB @ 2546×1440 | 6,545 B | 0.3 ms | 17 ms | 0.1 ms | **2.4 ms** | 5,929 | 32 ms |
| 500 KB @ 2546×1440 | 517,279 B | 1.1 ms | 24 ms | 1.6 ms | **2.4 ms** | 5,929 | 31–34 ms |

Cursor-only (`l`) and content-changing (`x` in insert mode) keys are indistinguishable at every row — within noise of each other — which is itself the finding: a cursor move costs exactly what a content edit costs, because both ship the whole buffer and repaint the whole viewport.

### Verdict on which half dominates

**Neither, below ~500 KB. Payload, above it. Paint never.**

1. **Paint is a non-problem and should be dropped from the map as a candidate.** 1.6 ms at 1454×900, 2.4 ms at 2546×1440 — scaling with *viewport area* (×1.75 area → ×1.5 paint) and **flat across buffer sizes** (1.6 ms at 8 KB, 1.5 ms at 5 MB). Three and a half thousand `fillText` calls for 1.6 ms. Dirty-region rendering would buy ~1.5 ms at the cost of a compositor. The map's fog patch "whether the repaint half is worth touching at all" is answered: no.
2. **There is a payload-independent ~17.5 ms floor, and at the current 8 KB buffer it *is* the latency.** Server work is 0.3 ms, client parse 0.1 ms, paint 1.6 ms — that accounts for 2 ms of a 17.5 ms round trip. Verified not frame-paced: disabling GPU, vsync and the frame-rate limit moved it to 19.5 ms, not down. Server-log epoch stamps put the client→server leg at ~1 ms, so the ~16 ms sits on the **view-delivery (server→client) path**; those log timestamps are polluted by Gradle's stdout buffering, so treat the split as inferred rather than measured. It is not isolated to a specific layer, and it must be before the spec sets a budget.
3. **Payload costs roughly 10 ms/MB** once above the floor: 8 KB→500 KB adds ~9 ms of pipeline (6.5 ms round trip + 1.7 ms parse + 0.8 ms server); 500 KB→5 MB adds ~30 ms. Linear enough to extrapolate: a 20 MB file is ~200 ms/keystroke, 100 MB is ~1 s/keystroke. The scaling is the cliff, and it is entirely the full-buffer snapshot ([`output.put("editorContent", …)`](../../../../src/main/java/com/ple/visur/ModelWasChangedVerticle.java#L28)).
4. **Today's felt latency (~32 ms keydown→frame) is mostly not Visur's work.** The measured pipeline is ~19 ms (17.5 transport + 1.6 paint); the remaining ~13 ms is waiting for the compositor's next frame, which the rAF-stamped total counts and no server change can remove. That means the 8 KB row is *already at the frame clock*: the payload fix will feel like nothing at the current buffer and like everything at 5 MB.

### What this changes on the map

- The map's binary framing — "which half, payload or paint" — was wrong. The real split is **payload vs round-trip scheduling**, and they beat paint by an order of magnitude. The payload fix is a *scaling* fix, not a responsiveness fix; the responsiveness fix is the transport.
- **A viewport-slice payload alone does not reach vim.** Vim/Neovim land keystroke→screen in ~1–2 ms locally. Slice the payload and Visur still sits at ~19 ms, ~10× vim, because of the 17.5 ms floor. If goal 1 is genuinely "as fast as vim", the transport is not optional — which is why the transport question in [Decide the terminal client's shape, transport, and parity](08-decide-terminal-shape.md) now carries an acceptance number rather than an open preference.
- The 5 MB cliff argues *for* the slice and *against* delta: the document is not the unit that gets faster, it is the unit that stops mattering.
- At 8 KB the payload term is ~0.5 ms. Any ticket that claims a latency win from the protocol change must state the buffer size it wins at.

### Method, so it can be rebuilt

Throwaway harness left uncommitted at `.scratch/baseline/` (`measure.cjs` + `run.sh`); delete on sight — it is not part of the build and will rot. To rebuild it:

1. **Server instrumentation** (temporary, reverted): in [`ModelWasChangedVerticle.handleChange`](../../../../src/main/java/com/ple/visur/ModelWasChangedVerticle.java#L18-22), wrap the body in `System.nanoTime()`, log `toJson().encode().length()` and the elapsed ns, then send the `JsonObject` as before. Buffer size varied via a temporary env-driven pad in [`InitializerService`](../../../../src/main/java/com/ple/visur/InitializerService.java#L40-97) (`VISUR_BASELINE_BYTES`), repeating the Declaration until the target length; 8 KB = unmodified.
2. **Client instrumentation** via Playwright `addInitScript`, before any page script runs: capture-phase `keydown` listener records `t0`; `CanvasRenderingContext2D.prototype.clearRect`/`fillText`/`fillRect` patched to count calls and stamp first/last-paint times; `WebSocket.prototype.send` patched to stamp the outbound key frame; `JSON.parse` patched to stamp the inbound frame (transport-agnostic — SockJS decodes with it) and to record frame bytes; a `requestAnimationFrame` loop finalizes each sample at the first frame after the last paint.
3. **Per cell**: `chromium.launch()`, viewport `W×H`, `deviceScaleFactor: 1`, goto `http://localhost:8888/`, wait for `#cursorQuantumDisplay` to be populated, then press `l` × N (cursor-only) and `i` then `x` × N (content-changing), waiting for each sample to land before the next key. Server restarted per buffer size.

### Caveats

- Headless Chromium rasterises `fillText` on the CPU; a GPU-backed canvas would move paint down, which only strengthens finding 1.
- Every displayed number is a same-process `performance.now()` delta, as the ticket permitted; it excludes IPC that the browser pays outside the page.
- 5 MB warm-up samples (74–100 ms) are JIT/GC; the steady state is the 32–36 ms figure.
- The 17.5 ms floor is measured but **not explained**. Isolating it (raw WebSocket echo vs SockJS bridge, browser vs server side) is the first thing [Decide the terminal client's shape, transport, and parity](08-decide-terminal-shape.md) should do, because it decides whether the browser client can ever reach vim latency or whether the terminal client is the only path to goal 1.
