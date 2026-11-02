# Lesson 9 replaced the itinerary's view-model and bus model with a source-grounded one

The itinerary (and the generated wiki, which repeats it) frames Lesson 9 as
"View = the snapshot" and "two verticles broadcast their address". Both are
backwards. Future lessons must use the corrected model below and must not
re-introduce the old framing.

1. **`View` is not the view model.** [`View.java`](../src/main/java/com/ple/visur/View.java)
   has exactly two public fields, `ca` and `editorContent`; the snapshot is a
   15-field inline `JsonObject` (+ a conditional `searchTarget`) built in
   [`ModelWasChangedVerticle.toJson()`](../src/main/java/com/ple/visur/ModelWasChangedVerticle.java#L24-52).
   The one `View` field that ships, `ca`, is assigned in
   [`visur.js:50`](../src/main/resources/static/visur.js#L50) and never read
   again — the cursor is drawn from `cursorQuantumStart`/`End` +
   `isAtQuantumStart`. The wiki's `view.md` lists the `toJson()` fields under
   "View" and is wrong.
2. **Each verticle consumes one address and sends a different one.**
   `KeyWasPressedVerticle` consumes `keyWasPressed` → sends `modelWasChanged`;
   `CanvasWasChangedVerticle` consumes `canvasWasChanged` → sends
   `modelWasChanged`; `ModelWasChangedVerticle` consumes `modelWasChanged` →
   sends `viewWasChanged`. Nothing broadcasts or publishes; every hop is
   `.send`. The itinerary's "ModelWasChangedVerticle broadcasts
   modelWasChanged" and "CanvasWasChangedVerticle broadcasts canvasWasChanged"
   invert the direction.
3. **Routing is by `EditorSubmode`, not `EditorMode`.** `KeymapMap` is
   `Map<EditorSubmode, Keymap>` (nine keys, including `navigate`, `insert`,
   `replace`), and dispatch peeks the submode stack. The editor-model slot is
   misleadingly named `modeToKeymap`.
4. **`modelWasChanged` reaches the browser via a relay, and that is
   load-bearing.** The browser registers a handler for it, so the SockJS bridge
   adds a *second* local consumer
   ([`EventBusBridgeImpl:243`](https://github.com/eclipse-vertx/vert.x/blob/4.4.4/vertx-web/src/main/java/io/vertx/ext/web/handler/sockjs/impl/EventBusBridgeImpl.java#L243));
   `send` then picks one handler round-robin
   ([`EventBusImpl:353-390`](https://github.com/eclipse-vertx/vert.x/blob/4.4.4/src/main/java/io/vertx/core/eventbus/impl/EventBusImpl.java#L353-390)).
   The browser's handler re-sends the same address
   ([`visur.js:116-119`](../src/main/resources/static/visur.js#L116-119)), so a
   keystroke's snapshot may take an extra browser round trip before the repaint.
   This is why `MainVerticle` permits *outbound* `modelWasChanged` at all. The
   mechanism is verified from Vert.x 4.4.4 source; that it was *intended* as a
   relay is inference (the outbound permission is the evidence) and is flagged
   as an open question in the lesson.

**Status:** active.

## Follow-up correction: viewport protocol and terminal renderer

The later renderer build supersedes the old wire-format and transport descriptions above. Keep the historical observations as records of the earlier implementation, but use the shipped contract below for current lessons and references.

- **Transport:** the browser still uses the SockJS bridge at `/eventbus`; the terminal uses plain JSON WebSocket at loopback `/ws`, with `{"address": ..., "body": ...}` envelopes. There is no SockJS-only client path.
- **Payload:** `View` and pure `Viewport` now produce a complete viewport-bounded `viewWasChanged` message. The `rows` array is already wrapped and carries absolute starts; cursor row/column are server-computed. The old `editorContent`, `ca`, and `canvasEnd` fields are absent.
- **Damage model:** a complete view remains the protocol contract; terminal paint optimization is separate. The terminal compares its previous and next cell grids and emits changed row runs, while `full: true`, first paint, and geometry changes force a repaint. Do not conflate terminal output damage with a protocol delta.
- **Viewport address:** the inbound name is `viewportWasChanged`; `canvasWasChanged` and `CanvasWasChangedVerticle` have been replaced by `ViewportWasChangedVerticle`. A viewport declaration triggers a full view update.
- **Exit path:** terminal-local `ZZ` and `ZQ` detach, while `ZD` asks the launcher/server to quit; process outcomes distinguish detach (`0`), quit (`3`), and lost server (`4`). Nothing in the editor's command-state service is the terminal quit binding.

Evidence: [[renderer-delivery spec](../docs/wayfinder/renderer-delivery/spec.md)] and its measured implementation verdicts in [[terminal-build conformance](../docs/wayfinder/terminal-build/conformance.md)]. The running implementation is in [[View.java](../src/main/java/com/ple/visur/View.java)], [[Viewport.java](../src/main/java/com/ple/visur/Viewport.java)], [[MainVerticle.java](../src/main/java/com/ple/visur/MainVerticle.java)], and [[terminal README](../terminal/README.md)].

**Evidence:** source review behind
[`lessons/0009-view-model-and-frontend-communication.html`](../lessons/0009-view-model-and-frontend-communication.html)
and its companion
[`reference/view-protocol-map.html`](../reference/view-protocol-map.html)
(new reusable component:
[`assets/protocol-trace.js`](../assets/protocol-trace.js)).
Not yet verified by learner exercises.

**Implications:** the "trust the source over the itinerary, then check the
framework too" pattern now has two hits (LR-0002, this record). For Lesson 10
(variables) treat the itinerary's naming as suspect as well — in particular
check whether `VariableMap`/`VisurVar` shapes match its "three variable
categories" claim before teaching it. Also: `nl`, `span`, `cursorQuantum`, and
`scopeQuantum` are global variables reached through `emc`, so the new reference
page is the natural bridge into that lesson.
