---
type: Sources
title: View
description: Server-side view model containing full editor state for browser rendering
resource: src/main/java/com/ple/visur/View.java
tags: [view, rendering, state]
generated: { by: visur-wiki-updater/pi, at: 2026-10-13T17:00:00Z }
updated: { by: visur-wiki-updater/pi, at: 2026-10-13T17:00:00Z }
---

# View

**Source:** [[View.java](../../src/main/java/com/ple/visur/View.java)]
**Date updated:** 2026-10-13

## Summary

The View class represents the complete editor state that is sent to the browser client for rendering.[^1] It contains all information needed to redraw the editor view:

- editorContent — the full text content
- span — the current span (selection width)
- canvasStart, canvasEnd — the visible region
- cursorQuantumStart, cursorQuantumEnd — quantum boundaries
- cursorQuantum — current quantum type
- isAtQuantumStart — whether cursor is at quantum start
- scopeQuantum — the scope quantum type
- editorMode, editorSubmode — current mode and submode
- searchTarget — active search target (if in search submode)
- command state information (commandStateContent, commandCursor, isInCommandState)[^2]

## Key Takeaways

- Complete editor state sent to browser for rendering
- Contains all visual elements: content, cursor, mode, span, quantum info

## Related Concepts

- [[canvas-cell](canvas-cell.md)] — single canvas cell
- [[visualization-color](visualization-color.md)] — canvas colors
- [[frontend-overview](frontend-overview.md)]

## References

[^1]: [[View.java](../../src/main/java/com/ple/visur/View.java#L1-6)] — defines all fields sent to browser client
[^2]: [[View.java](../../src/main/java/com/ple/visur/View.java#L1-6)] — View construction and property accessors
