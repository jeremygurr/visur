---
type: Sources
title: EditorModelKey
description: Typed keys for accessing editor state through the EditorModelCoupler
resource: src/main/java/com/ple/visur/EditorModelKey.java
tags: [state, keys, type-safety]
generated: { by: visur-wiki-updater/pi, at: 2026-10-13T17:00:00Z }
updated: { by: visur-wiki-updater/pi, at: 2026-10-13T17:00:00Z }
---

# EditorModelKey

**Source:** [[EditorModelKey.java](../../src/main/java/com/ple/visur/EditorModelKey.java)]
**Date updated:** 2026-10-13

## Summary

The EditorModelKey defines typed keys for accessing editor state through the EditorModelCoupler's LocalMap.[^1] Keys include: editorContent (String), cursorPositionDCHolder, globalVariableMap (VariableMap), canvasWidth, canvasHeight. These keys provide type-safe access to all editor state fields.

## Key Takeaways

- Typed keys for EditorModelCoupler's LocalMap
- Keys for content, cursor position, variables, canvas dimensions

## Related Pages

[[data-model-key](data-model-key.md)]
- [[editor-model-coupler](editor-model-coupler.md)]

## References

[^1]: [[EditorModelKey.java](../../src/main/java/com/ple/visur/EditorModelKey.java#L1-30)] — defines typed keys for editor state
