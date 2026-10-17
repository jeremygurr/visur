---
type: Sources
title: EditorModelCoupler
description: Central hub providing shared access to editor state across all verticles
resource: src/main/java/com/ple/visur/EditorModelCoupler.java
tags: [shared-state, coupling, verticle]
generated: { by: visur-wiki-updater/pi, at: 2026-10-13T17:00:00Z }
updated: { by: visur-wiki-updater/pi, at: 2026-10-13T17:00:00Z }
---

# EditorModelCoupler

**Source:** [[EditorModelCoupler.java](../../src/main/java/com/ple/visur/EditorModelCoupler.java)]
**Date updated:** 2026-10-13

## Summary

The EditorModelCoupler is the central shared state object used by all server-side verticles to access and modify editor state. It provides methods to get and set editor state through a `LocalMap<EditorModelKey, Object>` — a Vert.x shared data structure.[^1]

The coupler manages: editor content (String), next line indices (ArrayList<Integer>), cursor position data class holder (CursorPositionDCHolder), global variable map (VariableMap), canvas dimensions (width/height), and editor mode/submode state.[^2]

The EditorModelCoupler is created once and shared via the EditorModelCoupler reference in AbstractVisurVerticle, allowing all verticles to read and write to the same state space.

## Key Takeaways

- Central hub for all editor state
- Uses Vert.x LocalMap<EditorModelKey, Object> for shared state
- Manages content, cursor position, variables, and canvas dimensions
- Provides get/set methods for all state fields

## Entities & Concepts

- [[editor-mode-key](editor-mode-key.md)] — typed keys for state values
- [[editor-mode](editor-mode.md)] — editor state mode (normal/replace/insert)
- [[variable-map](variable-map.md)] — global variable storage
- [[editor-content-service](editor-content-service.md)] — service for reading editor state

## Relation to Other Wiki Pages

All verticles access state through the EditorModelCoupler. See [[editor-mode](editor-mode.md)] for mode management and [[variable-map](variable-map.md)] for variable storage.

## References

[^1]: [[EditorModelCoupler.java](../../src/main/java/com/ple/visur/EditorModelCoupler.java#L1-50)] — central state hub with LocalMap-based access methods
[^2]: [[EditorModelCoupler.java](../../src/main/java/com/ple/visur/EditorModelCoupler.java#L51-80)] — provides getters/setters for all editor state fields
