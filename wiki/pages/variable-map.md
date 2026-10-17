---
type: Concepts
title: variable-map
description: Global variable storage system supporting primitive, object, and brick variables
tags: [variables, state, storage]
sources:
  - resource: src/main/java/com/ple/visur/VariableMap.java
  - resource: src/main/java/com/ple/visur/VisurVar.java
  - resource: src/main/java/com/ple/visur/ObjectVisurVar.java
  - resource: src/main/java/com/ple/visur/BrickVisurVar.java
generated: { by: visur-wiki-updater/pi, at: 2026-10-13T17:00:00Z }
updated: { by: visur-wiki-updater/pi, at: 2026-10-13T17:00:00Z }
---

# Variable Map

## Description

The VariableMap system manages global variables accessible throughout the editor. Variables can hold primitive values, objects, or data class bricks.

**VariableMap** — A map from variable names to VisurVar objects. Supports `get(name)` and `put(name, var)` operations.

**VisurVar** — Abstract base class for all variable types. Has a name and a value.

**ObjectVisurVar** — Holds an arbitrary object as its value (e.g., a String, Integer, etc.).

**BrickVisurVar** — Holds a DataClassBrick as its value. Used for storing data class references (e.g., the `nl` global variable stores a BrickVisurVar wrapping an ArrayList of newline indices).

## Key Takeaways

- 3 variable types: base, object, and brick
- VariableMap manages global variable storage
- BrickVisurVar wraps DataClassBrick values

## Appearances in Sources

- [[VariableMap.java](../../src/main/java/com/ple/visur/VariableMap.java)] — name-to-variable map[^1]
- [[VisurVar.java](../../src/main/java/com/ple/visur/VisurVar.java)] — abstract variable with name and value[^2]
- [[ObjectVisurVar.java](../../src/main/java/com/ple/visur/ObjectVisurVar.java)] — holds arbitrary object values
- [[BrickVisurVar.java](../../src/main/java/com/ple/visur/BrickVisurVar.java)] — holds DataClassBrick values[^3]

## Related Concepts

- [[editor-model-coupler](editor-model-coupler.md)] — stores VariableMap in editor state
- [[editor-content-service](editor-content-service.md)] — reads/writes global variables

## Related Pages

[[initializer-service](initializer-service.md)]
- [[editor-model-coupler](editor-model-coupler.md)]

## References

[^1]: [[VariableMap.java](../../src/main/java/com/ple/visur/VariableMap.java#L1-30)] — map from variable names to VisurVar instances
[^2]: [[VisurVar.java](../../src/main/java/com/ple/visur/VisurVar.java#L1-12)] — abstract base with name and value
[^3]: [[BrickVisurVar.java](../../src/main/java/com/ple/visur/BrickVisurVar.java#L1-20)] — holds DataClassBrick references
