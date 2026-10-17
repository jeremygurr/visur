---
type: Sources
title: ChangeCursorQuantumHandler
description: Handler for changing the current cursor quantum type
resource: src/main/java/com/ple/visur/ChangeCursorQuantumHandler.java
tags: [handler, quantum, cursor]
generated: { by: visur-wiki-updater/pi, at: 2026-10-13T17:00:00Z }
updated: { by: visur-wiki-updater/pi, at: 2026-10-13T17:00:00Z }
---

# ChangeCursorQuantumHandler

**Source:** [[ChangeCursorQuantumHandler.java](../../src/main/java/com/ple/visur/ChangeCursorQuantumHandler.java)]
**Date updated:** 2026-10-13

## Summary

The ChangeCursorQuantumHandler processes key events that change the current cursor quantum type (character, line, regex, document). When a user switches quantum types (e.g., by typing `l` for line, `w` for word), this handler updates the editor state to reflect the new quantum type.[^1]

## Key Takeaways

- Handles quantum type changes
- Updates cursor quantum in editor state

## References

[^1]: [[ChangeCursorQuantumHandler.java](../../src/main/java/com/ple/visur/ChangeCursorQuantumHandler.java#L1-23)] — changes cursor quantum type
