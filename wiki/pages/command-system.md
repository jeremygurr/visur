---
type: Concepts
title: command-system
description: Visur's command language with Words, Operators, and compilation/execution services
tags: [command-language, parsing, execution, words]
sources:
  - resource: src/main/java/com/ple/visur/CommandCompileService.java
  - resource: src/main/java/com/ple/visur/CommandExecutionService.java
  - resource: src/main/java/com/ple/visur/CommandStateService.java
  - resource: src/main/java/com/ple/visur/VisurCommand.java
  - resource: src/main/java/com/ple/visur/Word.java
  - resource: src/main/java/com/ple/visur/LiteralNumberWord.java
  - resource: src/main/java/com/ple/visur/LiteralStringWord.java
  - resource: src/main/java/com/ple/visur/AssignmentWord.java
  - resource: src/main/java/com/ple/visur/NativeOpWord.java
  - resource: src/main/java/com/ple/visur/Operator.java
  - resource: src/main/java/com/ple/visur/CompiledWordResponse.java
generated: { by: visur-wiki-updater/pi, at: 2026-10-13T17:00:00Z }
updated: { by: visur-wiki-updater/pi, at: 2026-10-13T21:47:48Z }
---

# Command System

## Description

Visur implements a custom command language inspired by Vim's mode-based editing commands. The system is organized in three layers:

**Words** — The input language. A Word is a lexical token that the user types; `CommandCompileService` parses input into `Word` and its concrete subtypes:[^5]
- `LiteralNumberWord` — a numeric literal (e.g., `5` in `5j`); the number modifies the operator that follows it.[^6]
- `LiteralStringWord` — a quoted string (e.g., `"hello"`); used as a search target.
- `AssignmentWord` — a variable assignment (e.g., `x=5`); sets a variable to a value.
- `NativeOpWord` — a native operator word (e.g., `j` for down, `h` for left, `w` for word); each native word maps to a specific Operator.[^7]

**Operators** — The actions. An Operator performs a concrete editing operation on the editor model. There are 27 operators including:
- Movement: `AbsoluteMoveOp`, `RelativeMoveOp`
- Insertion: `InsertCharOp`, `InsertNewlineOp`, `ReplaceCharOp`
- Deletion: `DeletePreviousCharOp`, `DeleteCursorQuantumOp`
- Modification: `AssignmentOp`, `RecallOp`, `ChangeModeOp`, `ChangeScopeOp`
- Search: `SearchForwardOp`, `SearchBackwardOp`, `SearchInPreviousDirectionOp`
- Quantum: `QuantumStartOp`, `QuantumEndOp`, `ChangeCursorQuantumOp`
- Span: `SetSpanOp`, `ClearEDSOp`
- Submode: `PushSubmodeOp`, `RemoveSubmodeOp`
- Variables: `AddOperator` (variable register), `RemoveGlobalVarOp`
- Collection: `AddOperator` (add to list)

**Compilation and Execution** — The `CommandCompileService` parses user input into Words, converting them to the appropriate Word type. The `CommandExecutionService` then executes the compiled Words by applying their corresponding Operators to the editor model. `CommandStateService` manages command-mode state (the command line buffer and cursor).

`VisurCommand` represents a complete command parsed from user input, containing Words and an Operator to execute.[^3]

## Key Takeaways

- 4 Word types parse user input: numbers, strings, assignments, native ops
- 27 Operators perform editing actions (movement, insertion, deletion, search, etc.)
- CommandCompileService → CommandExecutionService pipeline
- CommandStateService manages command-mode editing state

## Appearances in Sources

- [[Word.java](../../src/main/java/com/ple/visur/Word.java)] — interface for all word types[^5]
- [[LiteralNumberWord.java](../../src/main/java/com/ple/visur/LiteralNumberWord.java)] — numeric literal word[^6]
- [[LiteralStringWord.java](../../src/main/java/com/ple/visur/LiteralStringWord.java)] — string literal word
- [[AssignmentWord.java](../../src/main/java/com/ple/visur/AssignmentWord.java)] — variable assignment word
- [[NativeOpWord.java](../../src/main/java/com/ple/visur/NativeOpWord.java)] — native operator word (j, h, etc.)[^7]
- [[Operator.java](../../src/main/java/com/ple/visur/Operator.java)] — interface for all operators
- [[CommandCompileService.java](../../src/main/java/com/ple/visur/CommandCompileService.java)] — parses input into Words[^1]
- [[CommandExecutionService.java](../../src/main/java/com/ple/visur/CommandExecutionService.java)] — executes Words as Operators[^2]
- [[CommandStateService.java](../../src/main/java/com/ple/visur/CommandStateService.java)] — command-mode state management
- [[CompiledWordResponse.java](../../src/main/java/com/ple/visur/CompiledWordResponse.java)] — result of compiling a Word, carrying its Operator, op info, and remaining sentence[^4]

## Related Concepts

- [[editor-mode](editor-mode.md)] — modes (normal/replace/insert) affect command interpretation
- [[quantum-system](quantum-system.md)] — quantums define edit scope
- [[editor-submode](editor-submode.md)] — submodes (span/scope/search) modify command behavior

## References

[^1]: [[CommandCompileService.java](../../src/main/java/com/ple/visur/CommandCompileService.java#L1-42)] — parses user input into Words
[^2]: [[CommandExecutionService.java](../../src/main/java/com/ple/visur/CommandExecutionService.java#L1-35)] — executes Words through their corresponding Operators
[^3]: [[VisurCommand.java](../../src/main/java/com/ple/visur/VisurCommand.java#L1-23)] — represents a complete parsed command
[^4]: [[CompiledWordResponse.java](../../src/main/java/com/ple/visur/CompiledWordResponse.java#L1-14)] — response from word compilation
[^5]: [[Word.java](../../src/main/java/com/ple/visur/Word.java#L1-5)] — base interface for all word types
[^6]: [[LiteralNumberWord.java](../../src/main/java/com/ple/visur/LiteralNumberWord.java#L1-20)] — numeric literal (modifier for following operators)
[^7]: [[NativeOpWord.java](../../src/main/java/com/ple/visur/NativeOpWord.java#L1-20)] — native operator word mapped to an Operator
