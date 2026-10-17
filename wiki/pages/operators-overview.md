---
type: Concepts
title: operators-overview
description: Overview of all operators that perform editing operations on the editor model
tags: [operators, editing, commands]
sources:
  - resource: src/main/java/com/ple/visur/Operator.java
  - resource: src/main/java/com/ple/visur/AbsoluteMoveOp.java
  - resource: src/main/java/com/ple/visur/RelativeMoveOp.java
  - resource: src/main/java/com/ple/visur/InsertCharOp.java
  - resource: src/main/java/com/ple/visur/DeletePreviousCharOp.java
  - resource: src/main/java/com/ple/visur/InsertNewlineOp.java
  - resource: src/main/java/com/ple/visur/ReplaceCharOp.java
  - resource: src/main/java/com/ple/visur/AssignmentOp.java
  - resource: src/main/java/com/ple/visur/RecallOp.java
  - resource: src/main/java/com/ple/visur/ChangeModeOp.java
  - resource: src/main/java/com/ple/visur/ChangeScopeOp.java
  - resource: src/main/java/com/ple/visur/SetSpanOp.java
  - resource: src/main/java/com/ple/visur/QuantumStartOp.java
  - resource: src/main/java/com/ple/visur/QuantumEndOp.java
  - resource: src/main/java/com/ple/visur/ClearEDSOp.java
  - resource: src/main/java/com/ple/visur/ChangeCursorQuantumOp.java
  - resource: src/main/java/com/ple/visur/PushSubmodeOp.java
  - resource: src/main/java/com/ple/visur/RemoveSubmodeOp.java
  - resource: src/main/java/com/ple/visur/SearchForwardOp.java
  - resource: src/main/java/com/ple/visur/SearchBackwardOp.java
  - resource: src/main/java/com/ple/visur/SearchInPreviousDirectionOp.java
  - resource: src/main/java/com/ple/visur/SearchNotInPreviousDirectionOp.java
  - resource: src/main/java/com/ple/visur/DeleteCursorQuantumOp.java
  - resource: src/main/java/com/ple/visur/AddOperator.java
  - resource: src/main/java/com/ple/visur/RemoveGlobalVarOp.java
  - resource: src/main/java/com/ple/visur/OperatorService.java
  - resource: src/main/java/com/ple/visur/ChangeCursorQuantumHandler.java
generated: { by: visur-wiki-updater/pi, at: 2026-10-13T17:00:00Z }
updated: { by: visur-wiki-updater/pi, at: 2026-10-13T17:00:00Z }
---

# Operators Overview

## Description

Operators perform concrete editing operations on the editor model. Each operator is triggered by a Word (or combination of Words) in the command system. Operators are organized into categories:

**Movement Operators:**
- `AbsoluteMoveOp` — Move cursor to absolute position
- `RelativeMoveOp` — Move cursor by relative offset (e.g., `5j` = move down 5 lines)

**Insertion Operators:**
- `InsertCharOp` — Insert a character at cursor position
- `InsertNewlineOp` — Insert newline, creating a new line
- `ReplaceCharOp` — Replace character at cursor position

**Deletion Operators:**
- `DeletePreviousCharOp` — Delete character before cursor (backspace)
- `DeleteCursorQuantumOp` — Delete the current quantum (selected region)

**Variable Operators:**
- `AssignmentOp` — Assign value to a variable (e.g., `x=5`)
- `RecallOp` — Recall a stored variable value
- `AddOperator` — Add value to a variable/list
- `RemoveGlobalVarOp` — Remove a global variable

**Mode/State Operators:**
- `ChangeModeOp` — Switch editor mode (normal/replace/insert)
- `ChangeScopeOp` — Change the scope quantum (character/line/regex/document)
- `SetSpanOp` — Set the span (selection width) for operations
- `ClearEDSOp` — Clear editor data stack
- `ChangeCursorQuantumOp` — Change the current quantum type
- `PushSubmodeOp` — Enter a submode (span/scope/search)
- `RemoveSubmodeOp` — Exit the current submode

**Search Operators:**
- `SearchForwardOp` — Search forward for pattern within scope
- `SearchBackwardOp` — Search backward for pattern within scope
- `SearchInPreviousDirectionOp` — Repeat search in same direction
- `SearchNotInPreviousDirectionOp` — Search in opposite direction

**Quantum Operators:**
- `QuantumStartOp` — Set quantum start boundary
- `QuantumEndOp` — Set quantum end boundary

**OperatorService** — Service managing operator lifecycle.
**ChangeCursorQuantumHandler** — Handler for cursor quantum changes.

## Key Takeaways

- 27 operators covering movement, insertion, deletion, variables, mode, search, and quantum operations
- Each operator performs a concrete editing action on the model
- Operators triggered by Words from the command compilation system

## Appearances in Sources

- [[Operator.java](../../src/main/java/com/ple/visur/Operator.java)] — base interface for all operators[^1]
- [[AbsoluteMoveOp.java](../../src/main/java/com/ple/visur/AbsoluteMoveOp.java)] — absolute cursor movement[^2]
- [[RelativeMoveOp.java](../../src/main/java/com/ple/visur/RelativeMoveOp.java)] — relative cursor movement
- [[InsertCharOp.java](../../src/main/java/com/ple/visur/InsertCharOp.java)] — character insertion[^3]
- [[DeletePreviousCharOp.java](../../src/main/java/com/ple/visur/DeletePreviousCharOp.java)] — backspace/delete
- [[InsertNewlineOp.java](../../src/main/java/com/ple/visur/InsertNewlineOp.java)] — newline insertion
- [[ReplaceCharOp.java](../../src/main/java/com/ple/visur/ReplaceCharOp.java)] — character replacement
- [[AssignmentOp.java](../../src/main/java/com/ple/visur/AssignmentOp.java)] — variable assignment
- [[RecallOp.java](../../src/main/java/com/ple/visur/RecallOp.java)] — variable recall
- [[SearchForwardOp.java](../../src/main/java/com/ple/visur/SearchForwardOp.java)] — forward search[^4]
- [[SearchBackwardOp.java](../../src/main/java/com/ple/visur/SearchBackwardOp.java)] — backward search

## Related Concepts

- [[command-system](command-system.md)] — operators are executed by the command execution service
- [[editor-mode](editor-mode.md)] — mode affects operator interpretation
- [[operator-service](operator-service.md)] — operator lifecycle
- [[CommandExecutionService.java](../../src/main/java/com/ple/visur/CommandExecutionService.java)] — command execution

## Related Pages

- [[operator-service](operator-service.md)]
- [[command-system](command-system.md)]

## References

[^1]: [[Operator.java](../../src/main/java/com/ple/visur/Operator.java#L1-28)] — interface for all operator types
[^2]: [[AbsoluteMoveOp.java](../../src/main/java/com/ple/visur/AbsoluteMoveOp.java#L1-10)] — moves cursor to absolute position
[^3]: [[InsertCharOp.java](../../src/main/java/com/ple/visur/InsertCharOp.java#L1-18)] — inserts character at cursor
[^4]: [[SearchForwardOp.java](../../src/main/java/com/ple/visur/SearchForwardOp.java#L1-30)] — searches forward within scope
