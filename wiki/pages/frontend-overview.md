---
type: Concepts
title: frontend-overview
description: Canvas browser and terminal renderers consume the same viewport-bounded view protocol
tags: [frontend, renderers, protocol]
sources:
  - resource: src/main/resources/static/visur.html
  - resource: src/main/resources/static/visur.js
  - resource: terminal/README.md
  - resource: terminal/src/main.rs
  - resource: src/main/java/com/ple/visur/MainVerticle.java
  - resource: docs/wayfinder/renderer-delivery/spec.md
generated: { by: pi, at: 2026-11-02T06:40:00Z }
updated: { by: pi, at: 2026-11-02T06:40:00Z }
---

# Frontend Overview

## Description

Visur has two renderers for the same server-side editor state. Both declare a viewport, send `keyWasPressed`, and paint complete `viewWasChanged` messages; the server supplies pre-wrapped rows and cursor coordinates.[^1]

**Canvas browser client** ([`visur.html`](../../src/main/resources/static/visur.html) and [`visur.js`](../../src/main/resources/static/visur.js)): connects through the SockJS event-bus bridge at `/eventbus`, sends `viewportWasChanged` in cell units, and paints the received rows on a 2D canvas. It does not wrap the document or send the old `canvasWasChanged` address.[^2]

**Terminal client** ([`terminal/`](../../terminal/)): a Rust process launched through [`visur`](../../visur). It connects over plain JSON WebSocket at `ws://127.0.0.1:8888/ws`; the socket frames carry event-bus `address`/`body` envelopes. It is a stateless painter, keeps a screen grid for damage comparison rather than editor state, and emits changed row runs. `ZZ` and `ZQ` detach; `ZD` requests server shutdown.[^3]

The separate prototype in [`frontEnd/`](../../frontEnd/) is a local canvas demo and is not one of the two server-connected renderers.

## Key Takeaways

- The two shipped renderers are the canvas client and the terminal client.
- Both use `viewportWasChanged` inbound and receive complete `viewWasChanged` payloads.
- Browser transport is SockJS `/eventbus`; terminal transport is loopback JSON WebSocket `/ws`.
- `editorContent` and `canvasWasChanged` are retired protocol fields/names.

## Appearances in Sources

- [[visur.html](../../src/main/resources/static/visur.html)] — browser renderer page
- [[visur.js](../../src/main/resources/static/visur.js)] — canvas client and browser bus messages
- [[terminal/README.md](../../terminal/README.md)] — terminal client behavior and run instructions
- [[terminal/src/main.rs](../../terminal/src/main.rs)] — terminal process entry point
- [[MainVerticle.java](../../src/main/java/com/ple/visur/MainVerticle.java)] — server transports and routing

## Related Concepts

- [[main-verticle](main-verticle.md)] — server routes and event-bus bridge
- [[view](view.md)] — complete viewport payload
- [[canvas-cell](canvas-cell.md)] — canvas drawing value
- [[visualization-color](visualization-color.md)] — canvas colors

## References

[^1]: [[spec.md](../../docs/wayfinder/renderer-delivery/spec.md#L49-68)] §1.1-1.2 L49-68 [synthesis] — complete viewport state, server-wrapped rows, and renderer responsibilities
[^2]: [[visur.js](../../src/main/resources/static/visur.js#L1-44)] L1-44 [synthesis] — browser event-bus connection, view handler, and viewport declaration
[^3]: [[README.md](../../terminal/README.md#L1-35)] L1-35 [synthesis] — terminal client transport, paint model, launcher, and exit bindings
