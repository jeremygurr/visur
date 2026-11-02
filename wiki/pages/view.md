---
type: Sources
title: View
description: Complete viewport-bounded state payload projected for every renderer
resource: src/main/java/com/ple/visur/View.java
tags: [view, rendering, protocol]
generated: { by: pi, at: 2026-11-02T06:40:00Z }
updated: { by: pi, at: 2026-11-02T06:40:00Z }
---

# View

**Source:** [[View.java](../../src/main/java/com/ple/visur/View.java)]
**Date updated:** 2026-11-02

## Summary

`View` is an immutable projection of the editor state required by a renderer. `View.of(EditorModelCoupler)` obtains the current origin and viewport dimensions, asks `Viewport` to lay out only the visible content as rows, derives the cursor row and column, and `render()` serializes the complete `viewWasChanged` payload.[^1]

The payload is bounded by the declared viewport, not by document length. It includes `full`, `origin`, `rows` (each with an absolute `start` and `text`), span and cursor information, quantum/mode/submode state, command state, and an always-present `searchTarget`. `Viewport` is a pure layout helper: its `rows` and `position` operations do not read the editor model or event bus.[^2]

The old `editorContent` whole-document field, browser-assigned `ca`, and `canvasEnd` are not in the current payload. `rows` replaces the document text; `origin` and the last row make `canvasEnd` derivable, while `ca` was unused.[^3]

## Key Takeaways

- `View` projects and serializes; `Viewport` performs pure viewport layout.
- Each `viewWasChanged` is a complete viewport description, not a patch.
- `rows` are pre-wrapped and carry absolute origins; cursor coordinates are `cursorRow` and `cursorCol`.
- `editorContent`, `ca`, and `canvasEnd` have been removed.

## Related Concepts

- [[frontend-overview](frontend-overview.md)] — canvas and terminal renderers
- [[bus-event](bus-event.md)] — event addresses
- [[canvas-was-changed-verticle](canvas-was-changed-verticle.md)] — viewport declaration path

## References

[^1]: [[View.java](../../src/main/java/com/ple/visur/View.java#L1-89)] L1-89 [synthesis] — projection, fields, viewport layout and serialization
[^2]: [[Viewport.java](../../src/main/java/com/ple/visur/Viewport.java#L1-100)] L1-100 [synthesis] — row layout and absolute-index-to-screen-position mapping
[^3]: [[spec.md](../../docs/wayfinder/renderer-delivery/spec.md#L87-95)] §1.3 L87-95 [synthesis] — removed fields and their replacements
