---
type: Concepts
title: editor-submode
description: Editor submodes (span, scope, searchForward, searchBackward, quantumStart, quantumEnd) that modify command behavior within a mode
tags: [submode, state, user-interface]
sources:
  - resource: src/main/java/com/ple/visur/EditorSubmode.java
  - resource: src/main/java/com/ple/visur/SpanSubmodeHandler.java
  - resource: src/main/java/com/ple/visur/ChangeScopeSubmodeHandler.java
  - resource: src/main/java/com/ple/visur/SearchForwardSubmodeHandler.java
  - resource: src/main/java/com/ple/visur/SearchBackwardSubmodeHandler.java
  - resource: src/main/java/com/ple/visur/QuantumStartSubmodeHandler.java
  - resource: src/main/java/com/ple/visur/QuantumEndSubmodeHandler.java
generated: { by: visur-wiki-updater/pi, at: 2026-10-13T17:00:00Z }
updated: { by: visur-wiki-updater/pi, at: 2026-10-13T21:47:48Z }
---

# Editor Submode

## Description

The editor submode modifies how commands behave within a mode (normal/replace/insert). There are 6 submodes in Visur:

**Span submode** — Sets a fixed span (length) for operations. When a span is set, commands operate on a fixed-width region rather than a quantum.

**Scope submode** — Chooses the quantum type for scope (character, line, regex, document). This determines what "scope" future commands will operate on.

**SearchForward submode** — Searches forward for a pattern within the chosen scope. The user types the search pattern, which is stored and executed when confirmed.

**SearchBackward submode** — Same as SearchForward but searches backward.

**QuantumStart submode** — Sets the start boundary of the current quantum.

**QuantumEnd submode** — Sets the end boundary of the current quantum.

Each submode has a dedicated handler for its key events: `SpanSubmodeHandler` sets the span width,[^4] `ChangeScopeSubmodeHandler` chooses the scope quantum, `SearchForwardSubmodeHandler` / `SearchBackwardSubmodeHandler` collect the search pattern, and `QuantumStartSubmodeHandler` / `QuantumEndSubmodeHandler` set the quantum boundaries.[^5] `ModeSwitchService` drives the transitions between submodes.

When in a submode, the UI displays the submode name (e.g., "SPAN SUBMODE", "SEARCHFORWARD (f) pattern") in the footer. The frontend's visur.js handles submode display in the status bar.[^3]

## Key Takeaways

- 6 submodes modify command behavior: span, scope, searchForward, searchBackward, quantumStart, quantumEnd
- Each submode has a dedicated handler processing key events
- UI displays current submode in footer status bar

## Appearances in Sources

- [[EditorSubmode.java](../../src/main/java/com/ple/visur/EditorSubmode.java)] — enum defining all 6 submodes[^1]
- [[SpanSubmodeHandler.java](../../src/main/java/com/ple/visur/SpanSubmodeHandler.java)] — span submode handler[^4]
- [[ChangeScopeSubmodeHandler.java](../../src/main/java/com/ple/visur/ChangeScopeSubmodeHandler.java)] — scope submode handler
- [[SearchForwardSubmodeHandler.java](../../src/main/java/com/ple/visur/SearchForwardSubmodeHandler.java)] — forward search handler[^2]
- [[SearchBackwardSubmodeHandler.java](../../src/main/java/com/ple/visur/SearchBackwardSubmodeHandler.java)] — backward search handler
- [[QuantumStartSubmodeHandler.java](../../src/main/java/com/ple/visur/QuantumStartSubmodeHandler.java)] — quantum start handler[^5]
- [[QuantumEndSubmodeHandler.java](../../src/main/java/com/ple/visur/QuantumEndSubmodeHandler.java)] — quantum end handler

## Related Concepts

- [[editor-mode](editor-mode.md)] — submodes operate within modes
- [[command-system](command-system.md)] — submodes are entered via commands
- [[ModeSwitchService.java](../../src/main/java/com/ple/visur/ModeSwitchService.java)] — submode transitions

## References

[^1]: [[EditorSubmode.java](../../src/main/java/com/ple/visur/EditorSubmode.java#L1-20)] — enum defining span, scope, searchForward, searchBackward, quantumStart, quantumEnd
[^2]: [[SearchForwardSubmodeHandler.java](../../src/main/java/com/ple/visur/SearchForwardSubmodeHandler.java#L1-30)] — handles search forward key events
[^3]: [[visur.js](../../src/main/resources/static/visur.js#L51-80)] — displays submode in footer status bar
[^4]: [[SpanSubmodeHandler.java](../../src/main/java/com/ple/visur/SpanSubmodeHandler.java#L1-26)] — handles span submode keys
[^5]: [[QuantumStartSubmodeHandler.java](../../src/main/java/com/ple/visur/QuantumStartSubmodeHandler.java#L1-24)] — handles quantum start boundaries
