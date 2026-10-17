 I now have a comprehensive understanding of the codebase — 113 Java files, Vert.x event-sourced
 architecture, Vim-inspired command system, custom data class hierarchy, Canvas 2D frontend. Here is the
 10-lesson itinerary:

 ────────────────────────────────────────────────────────────────────────────────

 10-Lesson Itinerary: "How Visur Works" — Training Guide for Java Developers

 ────────────────────────────────────────────────────────────────────────────────

 ### Lesson 1: Big Picture — What Is Visur and How It Runs

 Goal: Understand what the project is, its architecture pattern, and how to build/run it.

 - What Visur is (Vim-inspired rich text editor)
 - Vert.x event-driven architecture — what a "verticle" is
 - The 3 server verticles: ModelWasChangedVerticle, CanvasWasChangedVerticle, KeyWasPressedVerticle
 - How to build (./gradlew fatJar) and run (java -jar …)
 - The HTTP server on port 8888, WebSocket/sockJS bridge
 - [MainVerticle.java (../../src/main/java/com/ple/visur/MainVerticle.java)] — entry point, deploys all
   verticles, configures the EventBus bridge

 Source files: MainVerticle.java, AbstractVisurVerticle.java, build.gradle.kts, settings.gradle.kts

 ────────────────────────────────────────────────────────────────────────────────

 ### Lesson 2: The Editor Lifecycle — One Complete Request

 Goal: Trace a single keystroke from browser to server to browser.

 - The request loop: browser → EventBus → KeyWasPressedVerticle → Keymap → handler → operator → state
   mutation → ModelWasChangedVerticle → EventBus → browser re-render
 - How EditorModelCoupler provides shared state across all verticles (the single source of truth)
 - The EditorModelKey typed key system for state access
 - ServiceHolder — registry of editor services
 - InitializerService — one-time editor setup
 - EditorContentService — read/write utility methods

 Source files: EditorModelCoupler.java, EditorModelKey.java, ServiceHolder.java, InitializerService.java,
 EditorContentService.java, KeyWasPressedVerticle.java

 ────────────────────────────────────────────────────────────────────────────────

 ### Lesson 3: The Command Language — Words, Operators, and Pipelines

 Goal: Understand how user input becomes an editing action.

 - The 4-word taxonomy: LiteralNumberWord, LiteralStringWord, AssignmentWord, NativeOpWord (e.g., j, h)
 - The CommandCompileService — parses user keystrokes into Words
 - The CommandExecutionService — executes compiled Words by invoking their Operators
 - VisurCommand — represents a complete parsed command
 - CommandStateService — manages the command-line buffer state
 - The Operator interface — 30+ concrete operator implementations

 Source files: Word.java, CommandCompileService.java, CommandExecutionService.java, VisurCommand.java,
 CommandStateService.java, Operator.java

 ────────────────────────────────────────────────────────────────────────────────

 ### Lesson 4: Operators — The 30+ Editing Actions

 Goal: Categorize and understand every editing operation Visur supports.

 - Movement: AbsoluteMoveOp, RelativeMoveOp (e.g., 5j = move down 5 lines)
 - Insertion: InsertCharOp, InsertNewlineOp, ReplaceCharOp
 - Deletion: DeletePreviousCharOp, DeleteCursorQuantumOp
 - Variables: AssignmentOp, RecallOp, AddOperator, RemoveGlobalVarOp
 - Mode/State: ChangeModeOp, ChangeScopeOp, SetSpanOp, PushSubmodeOp, RemoveSubmodeOp
 - Search: SearchForwardOp, SearchBackwardOp, SearchInPreviousDirectionOp
 - Quantum: QuantumStartOp, QuantumEndOp
 - OperatorService — operator lifecycle management
 - ChangeCursorQuantumHandler — quantum type change handling

 Source files: All 30+ *Op.java files, OperatorService.java, ChangeCursorQuantumHandler.java

 ────────────────────────────────────────────────────────────────────────────────

 ### Lesson 5: Editor Modes and Submodes — The Context Layer

 Goal: Understand how editing behavior changes based on mode and submode state.

 - 3 modes: Normal, Replace, Insert — what each mode means for input
 - 6 submodes: Span, Scope, SearchForward, SearchBackward, QuantumStart, QuantumEnd
 - EditorMode and EditorSubmode enums
 - ModeSwitchService — transitions between modes
 - 3 mode handlers: EditingModeHandler, InsertModeHandler, ReplaceModeHandler
 - 6 submode handlers: SpanSubmodeHandler, ChangeScopeSubmodeHandler, SearchForwardSubmodeHandler,
   SearchBackwardSubmodeHandler, QuantumStartSubmodeHandler, QuantumEndSubmodeHandler

 Source files: EditorMode.java, EditorSubmode.java, ModeSwitchService.java, all *ModeHandler.java, all
 *SubmodeHandler.java files

 ────────────────────────────────────────────────────────────────────────────────

 ### Lesson 6: The Quantum System — Defining Edit Scope

 Goal: Understand the "region" concept — what can be selected, moved through, or operated on.

 - What a Quantum is (edit scope/region abstraction)
 - 4 quantum types: CharacterQuantum, LineQuantum, RegexQuantum, DocumentQuantum
 - KeyToQuantumName — mapping key names to quantum types
 - QuantumNameToQuantum — factory for creating quantums
 - RelativeLineBoundCalculator — computing line boundaries
 - How quantums interact with operators (e.g., "delete the current quantum")
 - How "scope" submode sets the quantum type for subsequent operations

 Source files: Quantum.java, CharacterQuantum.java, LineQuantum.java, RegexQuantum.java,
 DocumentQuantum.java, KeyToQuantumName.java, QuantumNameToQuantum.java, RelativeLineBoundCalculator.java

 ────────────────────────────────────────────────────────────────────────────────

 ### Lesson 7: The Data Class Hierarchy — A Nested Type System

 Goal: Understand the deep data structure framework used throughout the editor.

 - 5 levels of abstraction: DataClass (schema) → DataForm (value types) → DataClassBrick (node) →
   PrimitiveDataClassBrick (leaf) / CompoundDataClassBrick (composite) → OuterDataClassBrick (container) →
   LayeredDataClassBrick (layered container)
 - DataForm — value type definitions and conversion rules (JavaIntDF, IntArrayListDF)
 - DataFormBrick — typed value wrapper
 - CompoundDataClass — composite nodes mapping field names to children
 - LayeredDataClass — organizes content into compound layers
 - Conflict Resolution Rules:
     - Catalyst Permanence: when can values be safely removed
     - No Outer Left Behind: cascade unset through nested containers
 - documentation.txt — diagrams and explanations of conflict resolution

 Source files: DataClass.java, DataForm.java, DataClassBrick.java, DataFormBrick.java,
 PrimitiveDataClassBrick.java, CompoundDataClass.java, CompoundDataClassBrick.java, OuterDataClass.java,
 OuterDataClassBrick.java, LayeredDataClass.java, LayeredDataClassBrick.java, documentation.txt

 ────────────────────────────────────────────────────────────────────────────────

 ### Lesson 8: Cursor Position Management — Coordinates and Display

 Goal: Understand how cursor position is tracked and rendered.

 - CursorPositionDC — a specialized LayeredDataClass for cursor coordinates
 - Bidirectional decomposition: CAAndNLDC (absolute → line/column) ↔ CXCYAndNLDC (line/column → absolute)
 - CoordinatesDC — pure (x, y) coordinate representation
 - Coordinate.java — immutable coordinate for canvas rendering
 - MovementVector — relative cursor movement (dx, dy)
 - WholeNumberDC / WholeNumberListDC — number data classes used by cursor management
 - Canvas cell rendering — ViewCell — how a single character is displayed
 - VisurColor — color model for canvas visualization
 - LineWrapping — line wrapping management service

 Source files: CursorPositionDC.java, CAAndNLDC.java, CXCYAndNLDC.java, CoordinatesDC.java,
 Coordinate.java, MovementVector.java, WholeNumberDC.java, WholeNumberListDC.java, ViewCell.java,
 VisurColor.java, LineWrapping.java

 ────────────────────────────────────────────────────────────────────────────────

 ### Lesson 9: View Model and Frontend Communication

 Goal: Understand how server state is sent to the browser and rendered.

 - View — the complete editor state snapshot sent to the browser (content, cursor, mode, quantum, span,
   search target, command state)
 - BusEvent — event types on the Vert.x EventBus (4 addresses: viewWasChanged, keyWasPressed,
   canvasWasChanged, modelWasChanged)
 - The 3 verticles and their EventBus addresses:
     - KeyWasPressedVerticle — listens for keyWasPressed
     - ModelWasChangedVerticle — broadcasts modelWasChanged
     - CanvasWasChangedVerticle — broadcasts canvasWasChanged
 - Keymap system — maps keyboard events to actions, organized by mode
 - KeymapHandler — dispatches key events through mode-specific routing
 - Frontend (visur.js) — Canvas 2D rendering, WebSocket connection, drawing text and cursor

 Source files: View.java, BusEvent.java, Keymap.java, KeymapHandler.java, KeymapMap.java, KeyPressed.java,
 visur.js, visur.html, editingScreen.js, editingScreen.html

 ────────────────────────────────────────────────────────────────────────────────

 ### Lesson 10: Variables, Registers, and Global State

 Goal: Understand the variable system that persists across editing sessions.

 - VariableMap — global variable storage system
 - Three variable categories: primitives (numbers), objects, bricks (data class nodes)
 - VisurVar — base interface for variables
 - BrickVisurVar, ObjectVisurVar — typed variable implementations
 - How variables interact with the command system (assignment operators, recall operators)
 - How variables integrate with the editor state (via EditorModelCoupler)
 - The nl (next line indices) global variable — computed from editor content
 - How the frontend reads and displays global state

 Source files: VariableMap.java, VisurVar.java, BrickVisurVar.java, ObjectVisurVar.java, VariableMap.java,
 plus integration points with AssignmentOp.java, RecallOp.java, AddOperator.java, RemoveGlobalVarOp.java

 ────────────────────────────────────────────────────────────────────────────────

 ### Prerequisites for a Java Developer

 A Java developer should already be comfortable with:
 - Basic Java (objects, interfaces, enums, collections)
 - Event-driven programming concepts (listeners, callbacks, event loops)
 - Web protocols (HTTP, WebSockets) at a high level

 ### Recommended Order of Source File Exploration (Deep Dive)

 For a hands-on exploration after the lessons, read in this file order:
 1. MainVerticle.java (entry point)
 2. AbstractVisurVerticle.java (shared base)
 3. EditorModelCoupler.java (shared state hub)
 4. KeyWasPressedVerticle.java → Keymap.java → *Handler.java (input → dispatch → action)
 5. CommandCompileService.java → CommandExecutionService.java (parsing → execution)
 6. Operator.java + one example *Op.java (each editing action)
 7. DataClass.java → DataForm.java → *Brick.java (data hierarchy)
 8. CursorPositionDC.java (specialized data class)
 9. View.java (state snapshot to browser)
 10. visur.js (frontend rendering)
