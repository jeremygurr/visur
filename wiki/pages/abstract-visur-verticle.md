---
type: Sources
title: AbstractVisurVerticle
description: Base class for all Visur verticles providing shared event bus subscription and editor model access
resource: src/main/java/com/ple/visur/AbstractVisurVerticle.java
tags: [verticle, base-class, event-bus]
generated: { by: visur-wiki-updater/pi, at: 2026-10-13T17:00:00Z }
updated: { by: visur-wiki-updater/pi, at: 2026-10-13T17:00:00Z }
---

# AbstractVisurVerticle

**Source:** [[AbstractVisurVerticle.java](../../src/main/java/com/ple/visur/AbstractVisurVerticle.java)]
**Date updated:** 2026-10-13

## Summary

AbstractVisurVerticle is the abstract base class for all server-side verticles in Visur. It provides the shared infrastructure that all verticles need: a reference to the Vert.x event bus and the EditorModelCoupler for accessing shared editor state.[^1] All concrete verticles (ModelWasChangedVerticle, CanvasWasChangedVerticle, KeyWasPressedVerticle) extend this class and inherit these capabilities. The verticle also manages the subscription lifecycle — subscribing to the event bus address when started and unsubscribing when stopped.[^2]

The AbstractVisurVerticle defines the contract for verticle lifecycle management (start/stop) and provides access to the shared DataModelKey values through the EditorModelCoupler.

## Key Takeaways

- Base class for all Visur verticles
- Provides event bus subscription/unsubscription lifecycle
- Exposes EditorModelCoupler for shared state access

## Entities & Concepts

- [[editor-model-coupler](editor-model-coupler.md)] — shared state hub
- [[data-model-key](data-model-key.md)] — typed editor state keys
- [[service-holder](service-holder.md)] — service registry

## Relation to Other Wiki Pages

All concrete verticles extend AbstractVisurVerticle. See [[editor-model-coupler](editor-model-coupler.md)] for how verticles access shared state through the coupler.

## Related Concepts

- [[bus-event](bus-event.md)] — event bus types
- [[model-was-changed-verticle](model-was-changed-verticle.md)] — model change verticle

## Related Pages

- [[canvas-was-changed-verticle](canvas-was-changed-verticle.md)]
- [[key-was-pressed-verticle](key-was-pressed-verticle.md)]
- [[main-verticle](main-verticle.md)]
- [[model-was-changed-verticle](model-was-changed-verticle.md)]

## References

[^1]: [[AbstractVisurVerticle.java](../../src/main/java/com/ple/visur/AbstractVisurVerticle.java#L1-15)] — base class providing event bus and EditorModelCoupler access
[^2]: [[AbstractVisurVerticle.java](../../src/main/java/com/ple/visur/AbstractVisurVerticle.java#L1-15)] — lifecycle management (start/stop) with subscription handling
