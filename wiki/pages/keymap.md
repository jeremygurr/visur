---
type: Concepts
title: keymap
description: Key binding system mapping keyboard events to editor actions
tags: [input, keybindings, navigation]
sources:
  - resource: src/main/java/com/ple/visur/Keymap.java
  - resource: src/main/java/com/ple/visur/KeymapHandler.java
  - resource: src/main/java/com/ple/visur/KeymapMap.java
  - resource: src/main/java/com/ple/visur/KeyPressed.java
  - resource: src/main/java/com/ple/visur/KeyWasPressedVerticle.java
generated: { by: visur-wiki-updater/pi, at: 2026-10-13T17:00:00Z }
updated: { by: visur-wiki-updater/pi, at: 2026-10-13T17:00:00Z }
---

# Keymap

## Description

The Keymap system maps keyboard events to editor actions. When a key is pressed, the KeyWasPressedVerticle receives the event and dispatches it through the KeymapHandler, which looks up the appropriate action in the KeymapMap.

**Keymap** — Maps key names to their behavior in different modes. The keymap defines what each key does in normal, replace, and insert modes.

**KeymapHandler** — Processes key events by looking up the key in the current mode's keymap and executing the corresponding action. Handles mode-specific key interpretation.

**KeymapMap** — Stores the key-to-action mappings, organized by mode. Provides lookups for keys in the current editor mode.

**KeyPressed** — Represents a keyboard event with a key name. Created by KeyWasPressedVerticle and passed through the command system.

**KeyWasPressedVerticle** — Server-side verticle that listens on the "keyWasPressed" event bus address. Receives keyboard input from the browser, wraps it in a KeyPressed object, and dispatches it to the appropriate handler based on the current editor mode.

## Key Takeaways

- Keymap system maps keys to actions by mode (normal/replace/insert)
- KeyWasPressedVerticle receives keyboard events from browser via event bus
- KeymapHandler dispatches to mode-specific handlers

## Appearances in Sources

- [[Keymap.java](../../src/main/java/com/ple/visur/Keymap.java)] — key-to-action mappings[^1]
- [[KeymapHandler.java](../../src/main/java/com/ple/visur/KeymapHandler.java)] — processes key events through keymap lookup[^3]
- [[KeymapMap.java](../../src/main/java/com/ple/visur/KeymapMap.java)] — stores key bindings by mode
- [[KeyPressed.java](../../src/main/java/com/ple/visur/KeyPressed.java)] — represents a keyboard event
- [[KeyWasPressedVerticle.java](../../src/main/java/com/ple/visur/KeyWasPressedVerticle.java)] — verticle handling key events from browser[^2]

## Related Concepts

- [[editor-mode](editor-mode.md)] — key bindings are mode-dependent
- [[change-cursor-quantum-handler](change-cursor-quantum-handler.md)] — quantum change handler
- [[command-system](command-system.md)] — keys in normal mode generate commands

## Related Concepts

- [[change-cursor-quantum-handler](change-cursor-quantum-handler.md)] — quantum change handler
- [[key-was-pressed-verticle](key-was-pressed-verticle.md)] — keyboard input handling

## References

[^1]: [[Keymap.java](../../src/main/java/com/ple/visur/Keymap.java#L1-30)] — defines key-to-action mappings
[^2]: [[KeyWasPressedVerticle.java](../../src/main/java/com/ple/visur/KeyWasPressedVerticle.java#L1-35)] — verticle listening on "keyWasPressed" event bus address
[^3]: [[KeymapHandler.java](../../src/main/java/com/ple/visur/KeymapHandler.java#L1-5)] — processes key events through mode-specific keymap
