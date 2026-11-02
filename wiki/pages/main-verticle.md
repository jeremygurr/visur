---
type: Sources
title: MainVerticle
description: Application entry point deploying editor verticles and exposing browser and terminal transports
resource: src/main/java/com/ple/visur/MainVerticle.java
tags: [entrypoint, verticle, startup]
generated: { by: pi, at: 2026-11-02T06:40:00Z }
updated: { by: pi, at: 2026-11-02T06:40:00Z }
---

# MainVerticle

**Source:** [[MainVerticle.java](../../src/main/java/com/ple/visur/MainVerticle.java)]
**Date updated:** 2026-11-02

## Summary

`MainVerticle` initializes shared editor services, deploys `KeyWasPressedVerticle`, `ViewportWasChangedVerticle`, and `ModelWasChangedVerticle` in sequence, then starts the HTTP server on port 8888. The server starts only after the bus consumers are deployed, preventing an early viewport declaration from being silently dropped.[^1]

The router serves static browser files, mounts the SockJS event-bus bridge at `/eventbus`, and provides `/ws` for the terminal client. `/ws` accepts loopback clients only; each text frame is a JSON bus envelope (`address` and `body`). The bridge permits inbound `keyWasPressed` and `viewportWasChanged` and outbound `viewWasChanged`. `canvasWasChanged` and the former `CanvasWasChangedVerticle` are not part of the deployed architecture.[^2]

## Key Takeaways

- Deploys three verticles: key input, viewport declaration, and model-to-view projection.
- Opens port 8888 after deployment is complete.
- Browser uses SockJS at `/eventbus`; terminal uses plain JSON WebSocket at `/ws`.
- No Stomp bridge or `CanvasWasChangedVerticle` is started.

## Entities & Concepts

- [[model-was-changed-verticle](model-was-changed-verticle.md)] — projects and publishes view updates
- [[canvas-was-changed-verticle](canvas-was-changed-verticle.md)] — historical slug for the current `ViewportWasChangedVerticle` page
- [[key-was-pressed-verticle](key-was-pressed-verticle.md)] — handles keyboard input
- [[bus-event](bus-event.md)] — event addresses and directions
- [[frontend-overview](frontend-overview.md)] — browser and terminal renderers

## Relation to Other Wiki Pages

`MainVerticle` supplies the server transports; the renderer-neutral message contract is described in [[view](view.md)].

## References

[^1]: [[MainVerticle.java](../../src/main/java/com/ple/visur/MainVerticle.java#L20-40)] L20-40 [synthesis] — initializes services, deploys the three verticles, and starts HTTP after successful deployment
[^2]: [[MainVerticle.java](../../src/main/java/com/ple/visur/MainVerticle.java#L42-103)] L42-103 [synthesis] — configures bridge permissions, static files, `/eventbus`, loopback-only `/ws`, and bus-envelope dispatch
