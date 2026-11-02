---
type: Sources
title: ViewportWasChangedVerticle
description: Handles viewport declarations and triggers a full viewport view update
tags: [verticle, viewport, rendering]
resource: src/main/java/com/ple/visur/ViewportWasChangedVerticle.java
generated: { by: pi, at: 2026-11-02T06:40:00Z }
updated: { by: pi, at: 2026-11-02T06:40:00Z }
---

# ViewportWasChangedVerticle

**Source:** [[ViewportWasChangedVerticle.java](../../src/main/java/com/ple/visur/ViewportWasChangedVerticle.java)]
**Date updated:** 2026-11-02

## Summary

`ViewportWasChangedVerticle` consumes `viewportWasChanged`, stores the declared cell dimensions in the shared `EditorModelCoupler`, then sends `modelWasChanged` with `full: true`. That trigger makes [[model-was-changed-verticle](model-was-changed-verticle.md) project and publish a complete viewport payload. Both browser and terminal clients use the same inbound event; the terminal transport is the loopback `/ws` socket, while the browser uses the SockJS bridge.[^1]

The old `CanvasWasChangedVerticle` role and `canvasWasChanged` address have been retired. The historic page slug is retained for existing wiki links, but the deployed class is `ViewportWasChangedVerticle`.[^2]

## Key Takeaways

- Handles `viewportWasChanged` from renderers; dimensions are cells (`cols`, `rows`).
- Stores canvas width and height through `EditorModelCoupler` and requests `full: true` after layout changes.
- It does not build or send the view payload; `ModelWasChangedVerticle` does that.

## Entities & Concepts

- [[main-verticle](main-verticle.md)] — deploys this verticle and exposes renderer transports
- [[model-was-changed-verticle](model-was-changed-verticle.md)] — projects and publishes the view
- [[view](view.md)] — viewport-bounded payload and layout

## Relation to Other Wiki Pages

The viewport declaration is the inbound half of the protocol; [[bus-event](bus-event.md) lists the current addresses and [[frontend-overview](frontend-overview.md) describes both clients.

## References

[^1]: [[ViewportWasChangedVerticle.java](../../src/main/java/com/ple/visur/ViewportWasChangedVerticle.java#L1-30)] L1-30 [synthesis] — consumes the viewport declaration, stores dimensions, and triggers a full model update
[^2]: [[BusEvent.java](../../src/main/java/com/ple/visur/BusEvent.java#L1-16)] L1-16 [synthesis] — the current enum contains `viewportWasChanged` and no `canvasWasChanged`
