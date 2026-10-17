---
type: Concepts
title: data-form
description: Value type system with conversion between forms (JavaIntDF, IntArrayListDF, etc.)
tags: [data-structure, type-system, conversion]
sources:
  - resource: src/main/java/DataClass/DataForm.java
  - resource: src/main/java/DataClass/DataFormBrick.java
  - resource: src/main/java/CursorPositionDC/JavaIntDF.java
  - resource: src/main/java/CursorPositionDC/IntArrayListDF.java
  - resource: src/main/java/DataClass/DCHolder.java
  - resource: src/main/java/DataClass/Result.java
generated: { by: visur-wiki-updater/pi, at: 2026-10-13T17:00:00Z }
updated: { by: visur-wiki-updater/pi, at: 2026-10-13T17:00:00Z }
---

# Data Form

## Description

The DataForm system defines value types within the DataClass hierarchy. Each DataForm specifies:

1. **What values are valid** — via `makeBrick(Object val)` which returns Optional<DataFormBrick>
2. **Conversion to other forms** — via `convertTo()` and `convertFrom()`
3. **Conversion compatibility** — declared in `convertibleForms` array

Key DataForm implementations:

**JavaIntDF** — validates Integer values. Only Integer instances create valid DataFormBricks (null returns empty Optional).

**IntArrayListDF** — validates ArrayList<Integer> values. Supports both empty lists and lists of Integers.

DataFormBrick wraps a DataForm with a concrete value (Object). It supports `convertTo(toDF)` which calls `df.convertTo(toDF, val, dcHolder)` and returns a new DataFormBrick with the converted value and target form.

## Key Takeaways

- DataForm defines value types and conversion rules
- DataFormBrick = DataForm + concrete value
- JavaIntDF validates Integer; IntArrayListDF validates ArrayList<Integer>
- Conversion declared in convertibleForms array

## Appearances in Sources

- [[DataForm.java](../../src/main/java/DataClass/DataForm.java)] — abstract form with conversion methods[^1]
- [[DataFormBrick.java](../../src/main/java/DataClass/DataFormBrick.java)] — form + value wrapper[^2]
- [[JavaIntDF.java](../../src/main/java/CursorPositionDC/JavaIntDF.java)] — Integer validation[^3]
- [[IntArrayListDF.java](../../src/main/java/CursorPositionDC/IntArrayListDF.java)] — ArrayList<Integer> validation[^4]

## Related Concepts

- [[data-class-hierarchy](data-class-hierarchy.md)] — forms within data classes

## References

[^1]: [[DataForm.java](../../src/main/java/DataClass/DataForm.java#L1-50)] — abstract class with makeBrick, canConvertTo, convertTo, convertFrom
[^2]: [[DataFormBrick.java](../../src/main/java/DataClass/DataFormBrick.java#L1-40)] — holds DataForm + value, supports conversion
[^3]: [[JavaIntDF.java](../../src/main/java/CursorPositionDC/JavaIntDF.java#L1-25)] — validates Integer values
[^4]: [[IntArrayListDF.java](../../src/main/java/CursorPositionDC/IntArrayListDF.java#L1-25)] — validates ArrayList<Integer>
