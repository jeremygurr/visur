---
type: Concepts
title: editor-mode
description: Editor modes (normal, replace, insert) and submodes (span, scope, search) that control command behavior
tags: [mode, state, user-interface]
sources:
  - resource: src/main/java/com/ple/visur/EditorMode.java
  - resource: src/main/java/com/ple/visur/EditorSubmode.java
  - resource: src/main/java/com/ple/visur/ModeSwitchService.java
  - resource: src/main/java/com/ple/visur/InsertModeHandler.java
  - resource: src/main/java/com/ple/visur/EditingModeHandler.java
  - resource: src/main/java/com/ple/visur/ReplaceModeHandler.java
generated: { by: visur-wiki-updater/pi, at: 2026-10-13T17:00:00Z }
updated: { by: visur-wiki-updater/pi, at: 2026-10-13T17:00:00Z }
---

# Editor Mode

## Description

Visur implements a multi-mode editing system similar to Vim. The editor state is defined by both a mode and a submode:

**EditorMode** — The primary editing mode:
- Normal mode — Commands move the cursor and select/modify text
- Replace mode — Characters are replaced one-for-one
- Insert mode — Text is inserted as typed (like a traditional editor)

**EditorSubmode** — Modifies how commands behave within a mode:
- Span submode — Defines a fixed span (length) for operations
- Scope submode — Defines a quantum type (character/line/regex/document) for scope
- SearchForward submode — Search forward for a pattern within a scope
- SearchBackward submode — Search backward for a pattern within a scope

**ModeSwitchService** — Manages transitions between editor modes and submodes. Called when the user switches modes (e.g., pressing `i` to enter insert mode from normal mode).

**Mode Handlers** — Each mode has a handler that processes key events specific to that mode:
- `EditingModeHandler` (normal mode) — Handles command parsing and execution
- `InsertModeHandler` — Handles character insertion
- `ReplaceModeHandler` — Handles character replacement

Each submode also has a dedicated handler (`SpanSubmodeHandler`, `ChangeScopeSubmodeHandler`, `SearchForwardSubmodeHandler`, `SearchBackwardSubmodeHandler`, `QuantumStartSubmodeHandler`, `QuantumEndSubmodeHandler`) that handles submode-specific key events.

## Key Takeaways

- 3 modes: Normal, Replace, Insert
- 6 submodes: Span, Scope, SearchForward, SearchBackward, QuantumStart, QuantumEnd
- Each mode/submode has dedicated handler classes
- ModeSwitchService manages transitions

## Appearances in Sources

- [[EditorMode.java](../../src/main/java/com/ple/visur/EditorMode.java)] — enum of editing modes[^1]
- [[EditorSubmode.java](../../src/main/java/com/ple/visur/EditorSubmode.java)] — enum of submodes[^2]
- [[ModeSwitchService.java](../../src/main/java/com/ple/visur/ModeSwitchService.java)] — mode/submode transition management[^3]
- [[InsertModeHandler.java](../../src/main/java/com/ple/visur/InsertModeHandler.java)] — insert mode key handling[^4]
- [[EditingModeHandler.java](../../src/main/java/com/ple/visur/EditingModeHandler.java)] — normal mode command handling
- [[ReplaceModeHandler.java](../../src/main/java/com/ple/visur/ReplaceModeHandler.java)] — replace mode handling

## Related Concepts

- [[command-system](command-system.md)] — commands are interpreted differently in each mode
- [[keymap](keymap.md)] — key bindings are mode-dependent
- [[editor-submode](editor-submode.md)] — submodes operate within modes

## References

[^1]: [[EditorMode.java](../../src/main/java/com/ple/visur/EditorMode.java#L1-15)] — enum defining normal, replace, insert modes
[^2]: [[EditorSubmode.java](../../src/main/java/com/ple/visur/EditorSubmode.java#L1-20)] — enum defining span, scope, searchForward, searchBackward, quantumStart, quantumEnd submodes
[^3]: [[ModeSwitchService.java](../../src/main/java/com/ple/visur/ModeSwitchService.java#L1-38)] — manages mode and submode transitions
[^4]: [[InsertModeHandler.java](../../src/main/java/com/ple/visur/InsertModeHandler.java#L1-20)] — handles character insertion in insert mode
