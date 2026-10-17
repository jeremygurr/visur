---
type: Concepts
title: data-class-hierarchy
description: The layered data class system with nested type forms, bricks, and conflict-resolution rules
tags: [data-structure, type-system, consistency]
sources:
  - resource: src/main/java/DataClass/DataClass.java
  - resource: src/main/java/DataClass/DataClassBrick.java
  - resource: src/main/java/DataClass/PrimitiveDataClassBrick.java
  - resource: src/main/java/DataClass/CompoundDataClass.java
  - resource: src/main/java/DataClass/CompoundDataClassBrick.java
  - resource: src/main/java/DataClass/OuterDataClass.java
  - resource: src/main/java/DataClass/OuterDataClassBrick.java
  - resource: src/main/java/DataClass/LayeredDataClass.java
  - resource: src/main/java/DataClass/LayeredDataClassBrick.java
  - resource: src/main/java/DataClass/DataForm.java
  - resource: src/main/java/DataClass/DataFormBrick.java
  - resource: src/main/java/DataClass/DCHolder.java
  - resource: src/main/java/DataClass/Result.java
generated: { by: visur-wiki-updater/pi, at: 2026-10-13T17:00:00Z }
updated: { by: visur-wiki-updater/pi, at: 2026-10-13T21:47:48Z }
---

# Data Class Hierarchy

## Description

The DataClass system is a nested type-managed data structure framework used throughout Visur for managing editor state. It has a layered hierarchy with several key concepts:

**DataClass** — the abstract type definition, specifying what fields/properties a data object has and how they relate. This is the "schema" in the type system.

**DataForm** — within each DataClass, values can have different "forms" or representations. DataForm defines conversion rules between forms (e.g., how to convert a Number to a String). A DataForm declares which other forms it can be converted to via its `convertibleForms` array.

**DataFormBrick** — a typed value carrying both a DataForm and its concrete value (Object). It supports conversion via `convertTo()` and `convertFrom()` methods.

**DataClassBrick** — the base node in the data graph. It holds a reference to its DataClass type, a name, and an array of OuterDataClassBricks that contain it. This represents a leaf or intermediate node in the nested structure.

**PrimitiveDataClassBrick** — a leaf node carrying a single primitive value wrapped in a DataFormBrick. Supports `put(val)` which triggers conflict resolution through its outers, and `getOrCalc()` which recursively computes the value from outer structures if not directly set.

**CompoundDataClass / CompoundDataClassBrick** — a composite node that maps field names to DataClassBrick children (inners). The compound class defines what fields exist and how they compute from each other via `calcInternal()`.

**OuterDataClass / OuterDataClassBrick** — a container that holds a collection of OuterDataClassBricks. An outer can contain other outers (including compounds), and provides the `getOrCalc()` method which tries the brick itself first, then its outers.

**LayeredDataClass / LayeredDataClassBrick** — a specialized OuterDataClass that organizes its content into CompoundDataClass layers. The layered brick iterates through layers to find the result for a target name.[^5]

## Conflict Resolution Rules

The system implements two critical conflict resolution rules (from [[documentation.txt](../../src/main/java/DataClass/documentation.txt)]). The documentation draws the geometry as a target brick `w` sitting between neighbours `x` and `y` (`x, w, y`); the rules decide when putting one of them unsets the others.[^3]

**Rule 1 — Catalyst Permanence:** When `w.put()` is called, it checks whether removing the value would invalidate any neighbor's outer. If no brick exists that has a required neighbor without a target, the neighbor is NOT unset. However, if the neighbor's outer requires the target and no valid neighbor exists, the neighbor IS unset.[^3]

**Rule 2 — No Outer Left Behind:** When a brick's value changes via `put()`, ALL bricks in outer structures where nonEmpty > requiredSetValues get all their non-target inners unset. This cascades through nested outers, potentially unsetting deeply nested bricks that lost their required neighbors.[^4]

The algorithm: `removePossibleConflicts()` walks the outer tree, checking each outer's `getNumberOfNonEmpties()` against `getRequiredSetValues()`. When nonEmpties exceeds required, all inners not containing the target are unset via `remove()`.

## Key Takeaways

- 5 levels of abstraction: DataClass (schema) → DataForm (value types) → DataClassBrick (node) → PrimitiveDataClassBrick (leaf) / CompoundDataClassBrick (composite) → OuterDataClassBrick (container) → LayeredDataClassBrick (layered container)
- Two conflict resolution rules maintain consistency across nested structures
- `put()` triggers cascade of conflict checks through all containing outers
- `getOrCalc()` recursively computes values through the outer tree

## Appearances in Sources

- [[DataClass.java](../../src/main/java/DataClass/DataClass.java)] — abstract type definition
- [[DataClassBrick.java](../../src/main/java/DataClass/DataClassBrick.java)] — base node with outer relationships[^1]
- [[PrimitiveDataClassBrick.java](../../src/main/java/DataClass/PrimitiveDataClassBrick.java)] — leaf value node with conflict resolution[^2]
- [[CompoundDataClass.java](../../src/main/java/DataClass/CompoundDataClass.java)] — composite with named inners
- [[OuterDataClass.java](../../src/main/java/DataClass/OuterDataClass.java)] — container for outer bricks
- [[LayeredDataClass.java](../../src/main/java/DataClass/LayeredDataClass.java)] — layered containers with compound layers
- [[Result.java](../../src/main/java/DataClass/Result.java)] — generic wrapper pairing a value with an error string[^6]

## Related Concepts

- [[cursor-position-dc](cursor-position-dc.md)] — specialized LayeredDataClass for cursor management
- [[data-form](data-form.md)] — value type and conversion system

## References

[^1]: [[DataClassBrick.java](../../src/main/java/DataClass/DataClassBrick.java#L1-60)] — base class with outer relationships and unset neighbor propagation
[^2]: [[PrimitiveDataClassBrick.java](../../src/main/java/DataClass/PrimitiveDataClassBrick.java#L1-80)] — leaf node with put/getOrCalc and conflict resolution
[^3]: [[documentation.txt](../../src/main/java/DataClass/documentation.txt#L1-50)] — catalyst permanence rule with visual diagrams
[^4]: [[documentation.txt](../../src/main/java/DataClass/documentation.txt#L1-94)] — no outer left behind rule with nested conflict examples
[^5]: [[LayeredDataClassBrick.java](../../src/main/java/DataClass/LayeredDataClassBrick.java#L1-50)] — layered container iterating through compound layers
[^6]: [[Result.java](../../src/main/java/DataClass/Result.java#L1-20)] — error-result wrapper used throughout
