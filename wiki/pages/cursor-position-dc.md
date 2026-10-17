---
type: Concepts
title: cursor-position-dc
description: Layered data class managing cursor coordinates (cx, cy) and absolute position (ca) with newline indices
tags: [data-structure, cursor, coordinates]
sources:
  - resource: src/main/java/CursorPositionDC/CursorPositionDC.java
  - resource: src/main/java/CursorPositionDC/CoordinatesDC.java
  - resource: src/main/java/CursorPositionDC/CursorPositionDCHolder.java
  - resource: src/main/java/CursorPositionDC/WholeNumberDC.java
  - resource: src/main/java/CursorPositionDC/WholeNumberListDC.java
  - resource: src/main/java/CursorPositionDC/CAAndNLDC.java
  - resource: src/main/java/CursorPositionDC/CXCYAndNLDC.java
generated: { by: visur-wiki-updater/pi, at: 2026-10-13T17:00:00Z }
updated: { by: visur-wiki-updater/pi, at: 2026-10-13T17:00:00Z }
---

# Cursor Position DC

## Description

The CursorPositionDC is a specialized LayeredDataClass that manages cursor position through a multi-layered decomposition. It tracks cursor state using two coordinate systems:

- **ca (cursor absolute)**: The absolute character index in the document
- **cx (cursor x)**: The column position within the current line (0-based)  
- **cy (cursor y)**: The line number (0-based)
- **nl (newline list)**: An ArrayList of absolute positions where newlines occur

The system decomposes these coordinates through two compound classes:

**CAAndNLDC** — combines `ca` and `nl` to compute absolute position. Given `cx`, `cy`, and `nl`, calculates `ca = nl.get(cy > 0 ? cy-1 : 0) + cx`.

**CXCYAndNLDC** — combines `cx`, `cy`, and `nl` to compute from absolute position. Given `ca` and `nl`, iterates to find `cy` (the line containing `ca`), then computes `cx = ca - nl.get(cy-1)`.

**CoordinatesDC** — the top-level compound that contains both CAAndNLDC and CXCYAndNLDC as inners, allowing bidirectional computation between absolute and line-column coordinates.

**CursorPositionDC** — a LayeredDataClass containing the CoordinatesDC as its single layer.

The DCHolder (CursorPositionDCHolder) instantiates all components: JavaIntDF (validates Integer), IntArrayListDF (validates ArrayList<Integer>), WholeNumberDC (non-negative integers), WholeNumberListDC (lists of integers), and all compound/layered classes.

## Key Takeaways

- Manages cursor position through bidirectional coordinate computation
- CAAndNLDC computes absolute from line/column; CXCYAndNLDC computes line/column from absolute
- CoordinatesDC combines both decomposition views
- Uses WholeNumberDC (non-negative Integer) and WholeNumberListDC (ArrayList<Integer>) as primitive types

## Appearances in Sources

- [[CursorPositionDC.java](../../src/main/java/CursorPositionDC/CursorPositionDC.java)] — layered container for coordinates
- [[CoordinatesDC.java](../../src/main/java/CursorPositionDC/CoordinatesDC.java)] — bidirectional coordinate computation (CA↔CX/CY)[^1][^2]
- [[CursorPositionDCHolder.java](../../src/main/java/CursorPositionDC/CursorPositionDCHolder.java)] — holder creating all components[^3]
- [[CAAndNLDC.java](../../src/main/java/CursorPositionDC/CAAndNLDC.java)] — computes absolute position from line/column + newlines
- [[CXCYAndNLDC.java](../../src/main/java/CursorPositionDC/CXCYAndNLDC.java)] — computes line/column from absolute + newlines
- [[WholeNumberDC.java](../../src/main/java/CursorPositionDC/WholeNumberDC.java)] — non-negative integer primitive type[^4]
- [[WholeNumberListDC.java](../../src/main/java/CursorPositionDC/WholeNumberListDC.java)] — integer list primitive type[^5]

## Related Concepts

- [[data-class-hierarchy](data-class-hierarchy.md)] — uses the LayeredDataClass/CompoundDataClass hierarchy
- [[coordinate](coordinate.md)] — canvas coordinates
- [[movement-vector](movement-vector.md)] — directional movement
- [[editor-content-service](editor-content-service.md)]

## References

[^1]: [[CoordinatesDC.java](../../src/main/java/CursorPositionDC/CoordinatesDC.java#L1-50)] — calcCAAndNL computes ca from cx, cy, nl; calcCXCYAndNL computes cx, cy from ca, nl
[^2]: [[CoordinatesDC.java](../../src/main/java/CursorPositionDC/CoordinatesDC.java#L51-80)] — CXCYAndNLDC iterates newline list to find cy, then cx = ca - nl.get(cy-1)
[^3]: [[CursorPositionDCHolder.java](../../src/main/java/CursorPositionDC/CursorPositionDCHolder.java#L1-31)] — initializes all DC components, registers inner class relationships
[^4]: [[WholeNumberDC.java](../../src/main/java/CursorPositionDC/WholeNumberDC.java#L1-30)] — validates Integer >= 0 as valid input
[^5]: [[WholeNumberListDC.java](../../src/main/java/CursorPositionDC/WholeNumberListDC.java#L1-25)] — validates ArrayList<Integer> (including empty)
