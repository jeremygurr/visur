---
type: Sources
title: KeyWasPressedVerticle
description: Verticle handling keyboard input from the browser and dispatching to command system
resource: src/main/java/com/ple/visur/KeyWasPressedVerticle.java
tags: [verticle, input, keyboard]
generated: { by: visur-wiki-updater/pi, at: 2026-10-13T17:00:00Z }
updated: { by: visur-wiki-updater/pi, at: 2026-10-13T17:00:00Z }
---

# KeyWasPressedVerticle

**Source:** [[KeyWasPressedVerticle.java](../../src/main/java/com/ple/visur/KeyWasPressedVerticle.java)]
**Date updated:** 2026-10-13

## Summary

The KeyWasPressedVerticle receives keyboard events from the browser through the "keyWasPressed" event bus address.[^1] It wraps the key press into a KeyPressed object and dispatches it to the KeymapHandler based on the current editor mode (normal/replace/insert).[^2]

For normal mode, the key event is passed to the command compilation and execution pipeline. For insert/replace modes, the key is handled by the appropriate mode handler.

## Key Takeaways

- Listens on "keyWasPressed" event bus address
- Dispatches key events to mode-specific handlers
- Triggers command compilation in normal mode

## Entities & Concepts

- [[keymap](keymap.md)] — key binding system
- [[command-system](command-system.md)] — command interpretation in normal mode

## Relation to Other Wiki Pages

The entry point for all keyboard input. See [[keymap](keymap.md)] for mode-specific handling.

## Related Concepts

- [[keymap](keymap.md)] — key binding system
- [[service-holder](service-holder.md)] — service registry

## Related Pages

- [[abstract-visur-verticle](abstract-visur-verticle.md)]
- [[keymap](keymap.md)]

## References

[^1]: [[KeyWasPressedVerticle.java](../../src/main/java/com/ple/visur/KeyWasPressedVerticle.java#L1-35)] — listens on "keyWasPressed" event bus address
[^2]: [[KeyWasPressedVerticle.java](../../src/main/java/com/ple/visur/KeyWasPressedVerticle.java#L1-35)] — dispatches to mode-specific handlers
