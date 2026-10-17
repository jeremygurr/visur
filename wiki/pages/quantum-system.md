---
type: Concepts
title: quantum-system
description: Visur's quantum abstraction defining edit scope boundaries in the document
tags: [quantum, scope, navigation]
sources:
  - resource: src/main/java/com/ple/visur/Quantum.java
  - resource: src/main/java/com/ple/visur/CharacterQuantum.java
  - resource: src/main/java/com/ple/visur/RegexQuantum.java
  - resource: src/main/java/com/ple/visur/LineQuantum.java
  - resource: src/main/java/com/ple/visur/DocumentQuantum.java
  - resource: src/main/java/com/ple/visur/KeyToQuantumName.java
  - resource: src/main/java/com/ple/visur/QuantumNameToQuantum.java
  - resource: src/main/java/com/ple/visur/RelativeLineBoundCalculator.java
generated: { by: visur-wiki-updater/pi, at: 2026-10-13T17:00:00Z }
updated: { by: visur-wiki-updater/pi, at: 2026-10-13T17:00:00Z }
---

# Quantum System

## Description

The Quantum system defines the "scope" or "region" of an edit operation, similar to Vim's concept of text objects and motions. A Quantum represents a region in the document that can be selected, moved through, or operated on. There are several Quantum types:

**Quantum** — Abstract base defining the interface for all quantums. Each quantum type knows how to:
- Calculate its start and end positions
- Move the cursor through the quantum
- Determine boundaries relative to a position

**CharacterQuantum** — The atomic quantum, representing a single character position. Moving through a CharacterQuantum moves the cursor one character at a time.

**LineQuantum** — Represents a line in the document. Uses `RelativeLineBoundCalculator` to compute boundaries based on absolute cursor position and newline indices. A LineQuantum's boundaries are calculated from absolute positions to the start and end of the current line.

**RegexQuantum** — Represents a region matching a regex pattern. The search forwards/backwards operators use RegexQuantum to define search regions.

**DocumentQuantum** — The entire document as a single quantum. Moving through a DocumentQuantum spans the full document.

**KeyToQuantumName** — Maps key names to quantum type names (e.g., "character" → CharacterQuantum, "line" → LineQuantum).

**QuantumNameToQuantum** — Factory for creating Quantum instances from names, using the mapping from KeyToQuantumName.

**RelativeLineBoundCalculator** — Calculates line boundaries relative to a cursor position, using the newline index list (nl global variable) to determine line start/end positions.

## Key Takeaways

- 4 quantum types define different edit scopes: character, line, regex, document
- Quantum system manages edit boundaries (start/end positions)
- KeyToQuantumName maps key names to quantum types
- RelativeLineBoundCalculator computes line boundaries from absolute positions

## Appearances in Sources

- [[Quantum.java](../../src/main/java/com/ple/visur/Quantum.java)] — abstract base class for all quantums[^1]
- [[CharacterQuantum.java](../../src/main/java/com/ple/visur/CharacterQuantum.java)] — atomic single-character quantum
- [[LineQuantum.java](../../src/main/java/com/ple/visur/LineQuantum.java)] — line-scoped quantum with boundary calculation[^2]
- [[RegexQuantum.java](../../src/main/java/com/ple/visur/RegexQuantum.java)] — regex-matched region quantum
- [[DocumentQuantum.java](../../src/main/java/com/ple/visur/DocumentQuantum.java)] — full-document quantum
- [[KeyToQuantumName.java](../../src/main/java/com/ple/visur/KeyToQuantumName.java)] — key-to-quantum mapping[^4]
- [[QuantumNameToQuantum.java](../../src/main/java/com/ple/visur/QuantumNameToQuantum.java)] — quantum factory[^5]
- [[RelativeLineBoundCalculator.java](../../src/main/java/com/ple/visur/RelativeLineBoundCalculator.java)] — line boundary calculator[^3]

## Related Concepts

- [[command-system](command-system.md)] — commands specify which quantum to operate on
- [[editor-submode](editor-submode.md)] — submodes like span use quantums to define scope
- [[planned-features](planned-features.md)] — planned `FileQuantum`/`DirectoryQuantum` would extend this system

## References

[^1]: [[Quantum.java](../../src/main/java/com/ple/visur/Quantum.java#L1-30)] — abstract interface for quantum types with move/getBoundary methods
[^2]: [[LineQuantum.java](../../src/main/java/com/ple/visur/LineQuantum.java#L1-40)] — calculates line boundaries using absolute position and newline indices
[^3]: [[RelativeLineBoundCalculator.java](../../src/main/java/com/ple/visur/RelativeLineBoundCalculator.java#L1-30)] — computes line start/end from absolute position and newline list
[^4]: [[KeyToQuantumName.java](../../src/main/java/com/ple/visur/KeyToQuantumName.java#L1-17)] — maps string names to quantum classes
[^5]: [[QuantumNameToQuantum.java](../../src/main/java/com/ple/visur/QuantumNameToQuantum.java#L1-18)] — factory creating Quantum instances by name
