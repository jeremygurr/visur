# Index

Bundle root.

## Concepts

| Title | Type | Description |
|-------|------|-------------|
| [AbstractVisurVerticle](abstract-visur-verticle.md) | Sources | Base class for all Visur verticles providing shared event bus subscription an... |
| [BrowserInputService Proxy](browser-input-service-proxy.md) | Sources | Generated Vert.x service proxy that pushes browser key presses onto the event... |
| [BusEvent](bus-event.md) | Sources | Event types used in the Vert.x event bus communication between browser and se... |
| [ViewportWasChangedVerticle](canvas-was-changed-verticle.md) | Sources | Handles viewport declarations and triggers a full viewport view update |
| [ChangeCursorQuantumHandler](change-cursor-quantum-handler.md) | Sources | Handler for changing the current cursor quantum type |
| [Coordinate](coordinate.md) | Sources | Immutable coordinate representation (x, y) for cursor positioning on canvas |
| [DataModelKey](data-model-key.md) | Sources | Interface defining typed keys for data model state access |
| [EditorContentService](editor-content-service.md) | Sources | Service for reading and writing editor content, cursor position, and canvas d... |
| [EditorModelCoupler](editor-model-coupler.md) | Sources | Central hub providing shared access to editor state across all verticles |
| [EditorModelKey](editor-mode-key.md) | Sources | Typed keys for accessing editor state through the EditorModelCoupler |
| [InitializerService](initializer-service.md) | Sources | Service for initializing editor state and setting up initial configuration |
| [KeyWasPressedVerticle](key-was-pressed-verticle.md) | Sources | Verticle handling keyboard input from the browser and dispatching to command ... |
| [LineWrapping](line-wrapping.md) | Sources | Service for managing line wrapping behavior in the editor |
| [Logback Configuration](logging-configuration.md) | Sources | Logback setup for Visur — a STDOUT console appender and per-package log levels |
| [MainVerticle](main-verticle.md) | Sources | Application entry point deploying editor verticles and exposing browser and terminal transports |
| [ModelWasChangedVerticle](model-was-changed-verticle.md) | Sources | Verticle handling text model updates and dispatching model changes to clients |
| [MovementVector](movement-vector.md) | Sources | Directional movement vector for relative cursor movement |
| [OperatorService](operator-service.md) | Sources | Service managing the lifecycle and registration of editing operators |
| [ServiceHolder](service-holder.md) | Sources | Registry of editor services available through the EditorModelCoupler |
| [View](view.md) | Sources | Complete viewport-bounded state payload projected for every renderer |
| [ViewCell](canvas-cell.md) | Sources | A single editor cell holding one character and its foreground and background ... |
| [VisurColor](visualization-color.md) | Sources | Color representation for visualizing editor content on canvas |
| [command-system](command-system.md) | Concepts | Visur's command language with Words, Operators, and compilation/execution ser... |
| [cursor-position-dc](cursor-position-dc.md) | Concepts | Layered data class managing cursor coordinates (cx, cy) and absolute position... |
| [data-class-hierarchy](data-class-hierarchy.md) | Concepts | The layered data class system with nested type forms, bricks, and conflict-re... |
| [data-form](data-form.md) | Concepts | Value type system with conversion between forms (JavaIntDF, IntArrayListDF, e... |
| [editor-mode](editor-mode.md) | Concepts | Editor modes (normal, replace, insert) and submodes (span, scope, search) tha... |
| [editor-submode](editor-submode.md) | Concepts | Editor submodes (span, scope, searchForward, searchBackward, quantumStart, qu... |
| [frontend-overview](frontend-overview.md) | Concepts | Canvas browser and terminal renderers consume the same viewport-bounded view protocol |
| [keymap](keymap.md) | Concepts | Key binding system mapping keyboard events to editor actions |
| [operators-overview](operators-overview.md) | Concepts | Overview of all operators that perform editing operations on the editor model |
| [planned-features](planned-features.md) | Concepts | Features Visur's own docs name as future work — typed objects, multi-user e... |
| [quantum-system](quantum-system.md) | Concepts | Visur's quantum abstraction defining edit scope boundaries in the document |
| [variable-map](variable-map.md) | Concepts | Global variable storage system supporting primitive, object, and brick variables |

