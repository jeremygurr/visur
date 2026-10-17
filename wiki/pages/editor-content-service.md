---
type: Sources
title: EditorContentService
description: Service for reading and writing editor content, cursor position, and canvas dimensions
resource: src/main/java/CursorPositionDC/EditorContentService.java
tags: [service, content, cursor]
generated: { by: visur-wiki-updater/pi, at: 2026-10-13T17:00:00Z }
updated: { by: visur-wiki-updater/pi, at: 2026-10-13T17:00:00Z }
---

# EditorContentService

**Source:** [[EditorContentService.java](../../src/main/java/CursorPositionDC/EditorContentService.java)]
**Date updated:** 2026-10-13

## Summary

The EditorContentService provides utility methods for reading and writing editor state through the EditorModelCoupler.[^2] Key operations include:

- Getting/setting editor content (string)
- Getting/setting next line indices (ArrayList<Integer>)
- Getting cursor position data class holder
- Getting/setting global variables
- Calculating line boundaries for long lines
- Getting/setting canvas dimensions

The `updateNextLineIndices()` method recalculates newline positions from the editor content string and stores them in the `nl` global variable.[^1]

## Key Takeaways

- Reads/writes all editor state through EditorModelCoupler
- Calculates newline indices from content string
- Manages cursor position and canvas dimensions

## Related Concepts

- [[initializer-service](initializer-service.md)] — editor initialization
- [[editor-model-coupler](editor-model-coupler.md)] — state hub

## Related Pages

[[editor-model-coupler](editor-model-coupler.md)]
- [[initializer-service](initializer-service.md)]

## References

[^1]: [[EditorContentService.java](../../src/main/java/CursorPositionDC/EditorContentService.java#L1-50)] — content and newline index management
[^2]: [[EditorContentService.java](../../src/main/java/CursorPositionDC/EditorContentService.java#L51-80)] — global variable and cursor position access
