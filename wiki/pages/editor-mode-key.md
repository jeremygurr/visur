---
type: Sources
title: EditorModelKey
description: Typed keys for editor state stored through the EditorModelCoupler
resource: src/main/java/com/ple/visur/EditorModelKey.java
tags: [state, keys, type-safety]
generated: { by: pi, at: 2026-11-02T06:40:00Z }
updated: { by: pi, at: 2026-11-02T06:40:00Z }
---

# EditorModelKey

**Source:** [[EditorModelKey.java](../../src/main/java/com/ple/visur/EditorModelKey.java)]
**Date updated:** 2026-11-02

## Summary

`EditorModelKey` defines typed keys used by `EditorModelCoupler` to store editor data in its local map. The keys include editor content, cursor-position state, the global variable map, and viewport dimensions (`canvasWidth` and `canvasHeight`). The canvas-named dimensions remain model storage names; clients declare their available grid through the `viewportWasChanged` protocol.[^1]

## Key Takeaways

- Typed keys provide access to editor content, cursor state, variables, and viewport dimensions.
- `canvasWidth` and `canvasHeight` are internal state names, not the retired `canvasWasChanged` bus address.

## Related Pages

- [[data-model-key](data-model-key.md)]
- [[editor-model-coupler](editor-model-coupler.md)]
- [[bus-event](bus-event.md)]

## References

[^1]: [[EditorModelKey.java](../../src/main/java/com/ple/visur/EditorModelKey.java#L1-30)] L1-30 [synthesis] — typed keys for content, cursor position, variables, and viewport dimensions
