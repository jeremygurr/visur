---
type: Sources
title: ModelWasChangedVerticle
description: Verticle handling text model updates and dispatching model changes to clients
resource: src/main/java/com/ple/visur/ModelWasChangedVerticle.java
tags: [verticle, model, state]
generated: { by: visur-wiki-updater/pi, at: 2026-10-13T17:00:00Z }
updated: { by: visur-wiki-updater/pi, at: 2026-10-13T17:00:00Z }
---

# ModelWasChangedVerticle

**Source:** [[ModelWasChangedVerticle.java](../../src/main/java/com/ple/visur/ModelWasChangedVerticle.java)]
**Date updated:** 2026-10-13

## Summary

The ModelWasChangedVerticle listens on the "modelWasChanged" event bus address for text model updates.[^1] When the editor state changes (content modified, cursor moved, mode switched), this verticle is notified and broadcasts the new state to all connected clients through the "viewWasChanged" event bus address.

The verticle reads the current editor state from the EditorModelCoupler (content, cursor position, mode, submode, quantum bounds) and sends a view update message to clients via EventBus.send("viewWasChanged", message).[^2]

## Key Takeaways

- Listens on "modelWasChanged" event bus address
- Broadcasts full editor state to clients on "viewWasChanged"
- Reads state from EditorModelCoupler

## Entities & Concepts

- [[main-verticle](main-verticle.md)] — deploys this verticle
- [[abstract-visur-verticle](abstract-visur-verticle.md)] — base class

## Relation to Other Wiki Pages

Part of the event loop: KeyWasPressedVerticle → command processing → ModelWasChangedVerticle → View update → Browser re-render.

## Related Concepts

- [[view](view.md)] — server-side view model
- [[editor-model-coupler](editor-model-coupler.md)] — state hub

## References

[^1]: [[ModelWasChangedVerticle.java](../../src/main/java/com/ple/visur/ModelWasChangedVerticle.java#L1-50)] — listens on "modelWasChanged" and broadcasts to "viewWasChanged"
[^2]: [[ModelWasChangedVerticle.java](../../src/main/java/com/ple/visur/ModelWasChangedVerticle.java#L1-54)] — reads editor state and sends to clients
