# Mission: Understand the Visur codebase deeply enough to explain it

## Why
Visur is a large (~113 Java files) Vim-inspired, event-sourced rich-text editor
built on Vert.x, written primarily by another developer. The learner is a Java
developer who does not own this code, and is reverse-engineering its architecture
from the source in order to hold the whole system in their head and turn it into
a durable training course for other Java developers.

## Success looks like
- Trace one keystroke end to end (browser or terminal → EventBus → `KeyWasPressedVerticle` →
  keymap/handler → operator → model mutation → `ModelWasChangedVerticle` →
  viewport-bounded `viewWasChanged` → canvas or terminal re-render); explain how `ViewportWasChangedVerticle` handles `viewportWasChanged`.
- Place any `*Op.java` in the operator taxonomy and predict its effect.
- Predict mode/submode behavior for any key, including which submode is on top.
- Given cursor state and `nl`, derive `cx` / `cy` and say which coordinate
  decomposition is materialized.
- State the two data-class conflict rules (Catalyst Permanence, No Outer Left
  Behind) and apply them to a new node.
- Explain how the terminal client in `terminal/` consumes the same viewport-bounded view protocol as the canvas client, and how terminal damage painting and detach/quit behavior work.
- Add a new word, operator, or quantum by following existing patterns.
- Produce the full lesson set (`lessons/`) and reference material
  (`reference/`), and answer "where does X live and why" from the source or
  `wiki/` without guessing.

## Constraints
- Already a Java developer: comfortable with objects, interfaces, enums,
  collections, and basic event-driven patterns; HTTP/WebSocket at a high level.
- The code is not the learner's, so the repo's source and tests are the primary
  source of truth; `wiki/` is only a guide to them.
- Learning happens through short, self-contained HTML lessons that double as
  revisitable reference documents.

## Out of scope
- Vert.x internals beyond what Visur actually uses.
- Deep JavaScript / Canvas 2D craft (Lesson 9 covers the server↔browser
  interface, not front-end mastery).
- Rewriting the editor, and deployment / ops concerns.
