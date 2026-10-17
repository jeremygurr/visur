---
type: Sources
title: ViewCell
description: A single editor cell holding one character and its foreground and background colors
resource: src/main/java/com/ple/visur/ViewCell.java
tags: [view, rendering, cell]
generated: { by: visur-wiki-updater/pi, at: 2026-10-13T17:00:00Z }
updated: { by: visur-wiki-updater/pi, at: 2026-10-13T21:47:48Z }
---

# ViewCell

**Source:** [[ViewCell.java](../../src/main/java/com/ple/visur/ViewCell.java)]
**Date updated:** 2026-10-13

## Summary

ViewCell is a minimal data holder for one editor cell: a character plus a foreground and a background color.[^1] Its single constructor takes only the character, leaving the two color fields to be assigned separately.

## Key Takeaways

- Holds one character and its foreground and background colors
- Constructed from a character alone; colors are set independently
- The smallest cell unit the canvas view is built from

## Entities & Concepts

- [[view](view.md)] — the server-side view model
- [[visualization-color](visualization-color.md)] — the color type its fields hold

## References

[^1]: [[ViewCell.java](../../src/main/java/com/ple/visur/ViewCell.java#L1-11)] — fields `character`, `foregroundColor`, `backgroundColor` and a one-argument constructor
