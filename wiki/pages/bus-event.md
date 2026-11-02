---
type: Sources
title: BusEvent
description: Current event addresses connecting editor input, viewport declarations, model changes, and renderer views
resource: src/main/java/com/ple/visur/BusEvent.java
tags: [event-bus, communication, protocol]
generated: { by: pi, at: 2026-11-02T06:40:00Z }
updated: { by: pi, at: 2026-11-02T06:40:00Z }
---

# BusEvent

**Source:** [[BusEvent.java](../../src/main/java/com/ple/visur/BusEvent.java)]
**Date updated:** 2026-11-02

## Summary

`BusEvent` defines four current Vert.x event-bus addresses used by the editor and its renderers:[^1]

- `keyWasPressed` — client to server; keyboard input is handled by `KeyWasPressedVerticle`.
- `viewportWasChanged` — client to server; declares available content cells and is handled by `ViewportWasChangedVerticle`.
- `modelWasChanged` — internal server trigger; `ModelWasChangedVerticle` projects and publishes a view.
- `viewWasChanged` — server to all attached renderers; carries a complete viewport-bounded payload.

`canvasWasChanged` is removed. `editorContent` is not a bus address; it was a field in the old whole-document view payload and has been removed from the protocol.[^2]

## Key Takeaways

- The inbound address is `viewportWasChanged`, not `canvasWasChanged`.
- `modelWasChanged` triggers projection; `viewWasChanged` is published to all renderers.
- Browser transport uses the SockJS bridge at `/eventbus`; the terminal uses plain JSON WebSocket envelopes at `/ws`.

## Entities & Concepts

- [[main-verticle](main-verticle.md)] — configures bridge permissions and `/ws`
- [[view](view.md)] — defines the complete outbound view payload
- [[frontend-overview](frontend-overview.md)] — describes both renderers

## References

[^1]: [[BusEvent.java](../../src/main/java/com/ple/visur/BusEvent.java#L1-16)] L1-16 [synthesis] — declares the four current addresses and their direction comments
[^2]: [[spec.md](../../docs/wayfinder/renderer-delivery/spec.md#L90-104)] §1.3 L90-104 [synthesis] — lists removed payload fields and the reason `editorContent` was replaced
