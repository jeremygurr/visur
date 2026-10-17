---
type: Sources
title: LineWrapping
description: Service for managing line wrapping behavior in the editor
resource: src/main/java/com/ple/visur/LineWrapping.java
tags: [layout, wrapping, rendering]
generated: { by: visur-wiki-updater/pi, at: 2026-10-13T17:00:00Z }
updated: { by: visur-wiki-updater/pi, at: 2026-10-13T17:00:00Z }
---

# LineWrapping

**Source:** [[LineWrapping.java](../../src/main/java/com/ple/visur/LineWrapping.java)]
**Date updated:** 2026-10-13

## Summary

The LineWrapping service manages line wrapping behavior in the editor. When text exceeds the canvas width, lines wrap to the next line. This service manages the newline index list (nl global variable) that tracks absolute positions of line breaks.[^1]

## Key Takeaways

- Manages line wrapping behavior
- Tracks newline positions for line-based operations

## Related Concepts

- [[View.java](../../src/main/java/com/ple/visur/View.java)] — view contains lineWrapping
- [[canvas-was-changed-verticle](canvas-was-changed-verticle.md)] — handles line wrap changes

## References

[^1]: [[LineWrapping.java](../../src/main/java/com/ple/visur/LineWrapping.java#L1-6)] — line wrapping management
