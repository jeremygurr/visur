---
type: Overview
title: Overview
description: Entrance to the Wiki Pages
generated: { by: visur-wiki-updater/pi, at: 2026-10-13T17:00:00Z }
updated: { by: visur-wiki-updater/pi, at: 2026-10-13T17:00:00Z }
---

# Introduction

Visur is a **Vim-inspired rich text editor** built as a Vert.x event-sourced application. It implements a mode-based editing system with a custom command language supporting movement, insertion, deletion, searching, and variable management. The frontend uses an HTML5 canvas for rendering, with real-time WebSocket communication to a Java backend via the Vert.x event bus.

The project consists of:
- **Server-side Java code** (~100 files) implementing the editor's event architecture, command system, data classes, and editor state management
- **Frontend JavaScript** using Canvas 2D rendering and sockJS/EventBus for browser-server communication
- **Build system** using Gradle with Kotlin DSL

## Zones

### Server Core (`src/main/java/com/ple/visur/`)
The event-sourced backend. Contains all verticles (MainVerticle, ModelWasChangedVerticle, CanvasWasChangedVerticle, KeyWasPressedVerticle), the command system (Words, Operators, compilation/execution services), and editor state management (EditorMode, EditorSubmode, Keymap, VariableMap).

### Data Classes (`src/main/java/DataClass/`)
A nested type-managed data structure system with 5 levels of abstraction: DataClass (schema) → DataForm (value types) → DataClassBrick (node) → PrimitiveDataClassBrick (leaf) → CompoundDataClassBrick (composite) → OuterDataClassBrick (container) → LayeredDataClassBrick (layered container). Includes conflict resolution rules (Catalyst Permanence, No Outer Left Behind).

### Cursor Position (`src/main/java/CursorPositionDC/`)
A specialized LayeredDataClass managing cursor coordinates (cx, cy, ca) through bidirectional decomposition: CAAndNLDC (absolute from line/column) and CXCYAndNLDC (line/column from absolute).

### Frontend (`src/main/resources/static/` and `frontEnd/`)
- `visur.js` + `visur.html`: Production frontend with WebSocket/EventBus communication, canvas rendering, cursor visualization
- `editingScreen.js` + `editingScreen.html`: Development prototype with h/j/k/l navigation

### Build Configuration (`build.gradle.kts`, `settings.gradle.kts`)
Gradle build with Vert.x dependencies, Fat/JAR packaging, and main class configuration.

### Configuration (`src/main/resources/`)
Logback logging configuration ([[logback.xml](../src/main/resources/logback.xml)]).

## Core Dependencies

- **Vert.x** — Reactive toolkit for event-driven architecture (event bus, HTTP server, WebSocket)
- **sockJS** — Browser-side WebSocket fallback for event bus communication
- **Vert.x EventBus Bridge** — JavaScript bridge for browser-to-server messaging
- **Logback** — Logging framework
- **Gradle** — Build tool with Kotlin DSL

## Building

```bash
./gradlew build          # Compile and test
./gradlew jar            # Build JAR
./gradlew fatJar         # Build fat JAR with all dependencies
```

Run with: `java -jar build/libs/visur-*.jar` (starts on port 8888)

## Project Status

The editor implements a complete Vim-like editing experience with:
- ✅ Mode system (normal, replace, insert)
- ✅ Command system (4 word types, 30+ operators)
- ✅ Quantum system (character, line, regex, document)
- ✅ Search system (forward/backward with scope)
- ✅ Variable system (global variables, registers)
- ✅ Canvas rendering (2D, cell-based)
- ✅ Real-time sync (WebSocket/EventBus)
- ✅ Nested data class system with conflict resolution

## Additional Reference Pages

Additional wiki pages covering specific topics:
- [[bus-event](pages/bus-event.md)] — Event bus communication types
- [[browser-input-service-proxy](pages/browser-input-service-proxy.md)] — Generated key-press service proxy
- [[change-cursor-quantum-handler](pages/change-cursor-quantum-handler.md)] — Quantum type change handler
- [[coordinate](pages/coordinate.md)] — Canvas coordinate system
- [[editor-mode-key](pages/editor-mode-key.md)] — Typed editor state keys
- [[editor-submode](pages/editor-submode.md)] — Submode system
- [[initializer-service](pages/initializer-service.md)] — Editor initialization
- [[line-wrapping](pages/line-wrapping.md)] — Line wrapping management
- [[logging-configuration](pages/logging-configuration.md)] — Logback logging configuration
- [[movement-vector](pages/movement-vector.md)] — Directional movement
- [[operator-service](pages/operator-service.md)] — Operator lifecycle management
- [[operators-overview](pages/operators-overview.md)] — All editing operators
- [[planned-features](pages/planned-features.md)] — Features named as future work
- [[view](pages/view.md)] — Server-side view model
- [[canvas-cell](pages/canvas-cell.md)] — Single canvas cell
- [[visualization-color](pages/visualization-color.md)] — Canvas color representation

## Index

See [[index](pages/index.md)] for a complete listing of wiki pages.
