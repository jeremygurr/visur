---
type: Sources
title: InitializerService
description: Service for initializing editor state and setting up initial configuration
resource: src/main/java/com/ple/visur/InitializerService.java
tags: [service, initialization, setup]
generated: { by: visur-wiki-updater/pi, at: 2026-10-13T17:00:00Z }
updated: { by: visur-wiki-updater/pi, at: 2026-10-13T17:00:00Z }
---

# InitializerService

**Source:** [[InitializerService.java](../../src/main/java/com/ple/visur/InitializerService.java)]
**Date updated:** 2026-10-13

## Summary

The InitializerService sets up initial editor state when the application starts. It creates the default CursorPositionDCHolder, initializes the VariableMap with global variables (including the `nl` variable for newline indices), and configures the EditorModelCoupler with initial values.[^1]

## Key Takeaways

- Sets up initial editor state
- Creates default CursorPositionDCHolder
- Initializes global variables

## Related Pages

- [[service-holder](service-holder.md)]
- [[variable-map](variable-map.md)]
- [[editor-model-coupler](editor-model-coupler.md)]

## References

[^1]: [[InitializerService.java](../../src/main/java/com/ple/visur/InitializerService.java#L1-40)] — initialization of editor state and global variables
