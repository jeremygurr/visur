---
type: Sources
title: ServiceHolder
description: Registry of editor services available through the EditorModelCoupler
resource: src/main/java/com/ple/visur/ServiceHolder.java
tags: [services, registry, initialization]
generated: { by: visur-wiki-updater/pi, at: 2026-10-13T17:00:00Z }
updated: { by: visur-wiki-updater/pi, at: 2026-10-13T17:00:00Z }
---

# ServiceHolder

**Source:** [[ServiceHolder.java](../../src/main/java/com/ple/visur/ServiceHolder.java)]
**Date updated:** 2026-10-13

## Summary

The ServiceHolder holds references to all editor services (InitializerService, OperatorService, CommandCompileService, CommandExecutionService, CommandStateService, ModeSwitchService, EditorContentService) and makes them accessible through the EditorModelCoupler. Services are initialized once and shared across all verticles.[^1]

## Key Takeaways

- Central registry of editor services
- Services shared across all verticles through EditorModelCoupler

## Related Concepts

- [[editor-model-coupler](editor-model-coupler.md)] — state hub
- [[initializer-service](initializer-service.md)] — editor initialization

## References

[^1]: [[ServiceHolder.java](../../src/main/java/com/ple/visur/ServiceHolder.java#L1-30)] — holds all editor service instances
