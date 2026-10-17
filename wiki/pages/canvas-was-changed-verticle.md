---
type: Sources
title: CanvasWasChangedVerticle
description: Verticle handling canvas/viewport updates and sending rendering info to clients
resource: src/main/java/com/ple/visur/CanvasWasChangedVerticle.java
tags: [verticle, canvas, rendering]
generated: { by: visur-wiki-updater/pi, at: 2026-10-13T17:00:00Z }
updated: { by: visur-wiki-updater/pi, at: 2026-10-13T17:00:00Z }
---

# CanvasWasChangedVerticle

**Source:** [[CanvasWasChangedVerticle.java](../../src/main/java/com/ple/visur/CanvasWasChangedVerticle.java)]
**Date updated:** 2026-10-13

## Summary

The CanvasWasChangedVerticle handles canvas/viewport update requests from clients. When a client resizes the canvas or requests a viewport redraw, this verticle processes the canvas dimensions, updates the EditorModelCoupler with the new canvas width and height, and calculates the visible region (canvasStart, canvasEnd) based on cursor position and canvas size.[^1]

## Key Takeaways

- Handles canvas resize events from browser
- Updates canvas dimensions in EditorModelCoupler
- Calculates visible region (canvasStart, canvasEnd)[^2]

## Entities & Concepts

- [[main-verticle](main-verticle.md)] — deploys this verticle
- [[editor-model-coupler](editor-model-coupler.md)] — stores canvas dimensions

## Relation to Other Wiki Pages

Coordinates with ModelWasChangedVerticle: canvas size determines what portion of the editor is rendered.

## Related Concepts

- [[view](view.md)] — server-side view model
- [[line-wrapping](line-wrapping.md)] — line wrapping management

## Related Pages

- [[abstract-visur-verticle](abstract-visur-verticle.md)]
- [[view](view.md)]
- [[line-wrapping](line-wrapping.md)]

## References

[^1]: [[CanvasWasChangedVerticle.java](../../src/main/java/com/ple/visur/CanvasWasChangedVerticle.java#L1-37)] — processes canvas resize and updates dimensions
[^2]: [[CanvasWasChangedVerticle.java](../../src/main/java/com/ple/visur/CanvasWasChangedVerticle.java#L1-37)] — calculates visible region (canvasStart, canvasEnd)
