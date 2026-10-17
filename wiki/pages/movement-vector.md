---
type: Sources
title: MovementVector
description: Directional movement vector for relative cursor movement
resource: src/main/java/com/ple/visur/MovementVector.java
tags: [movement, direction, relative]
generated: { by: visur-wiki-updater/pi, at: 2026-10-13T17:00:00Z }
updated: { by: visur-wiki-updater/pi, at: 2026-10-13T17:00:00Z }
---

# MovementVector

**Source:** [[MovementVector.java](../../src/main/java/com/ple/visur/MovementVector.java)]
**Date updated:** 2026-10-13

## Summary

The MovementVector represents a directional movement (delta x, delta y) used for relative cursor movement operations.[^1] When a user types a motion command (e.g., `3j` = move down 3 lines), the relative offset is represented as a MovementVector.

## Key Takeaways

- Represents (dx, dy) directional offset
- Used for relative movement (RelativeMoveOp)

## References

[^1]: [[MovementVector.java](../../src/main/java/com/ple/visur/MovementVector.java#L1-10)] — directional offset for relative movement
