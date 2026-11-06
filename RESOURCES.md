# Visur Resources

Internal sources rank first by design: this repo is the source of truth, and the
course exists to make it legible. External sources are for the framework and the
behaviour Visur imitates, never for claims about this codebase.

## Knowledge

### Internal — source of truth

- [`src/main/java/com/ple/visur/`](src/main/java/com/ple/visur/) — the event-sourced
  server core: verticles, command system (`Word`s, `*Op`s),
  `EditorModelCoupler`, modes/submodes, keymaps, quantums, variables.
  Use for: anything about request flow, editing actions, and editor state.
- [`src/main/java/DataClass/`](src/main/java/DataClass/) — the schema/brick type
  system. Start with
  [`documentation.txt`](src/main/java/DataClass/documentation.txt): six worked
  ASCII examples of the two conflict rules. Use for: `requiredSetValues`,
  `getOrCalc`, `put` semantics, derived values — the hardest part of the codebase.
- [`src/main/java/CursorPositionDC/`](src/main/java/CursorPositionDC/) — cursor
  coordinates and editor content service. Use for: `ca`/`cx`/`cy`/`nl`, line
  indices, and the lazy derivation graph.
- [`src/test/CursorPositionDC/TestCursorPositionDC.java`](src/test/CursorPositionDC/TestCursorPositionDC.java)
  and [`TestDF.java`](src/test/CursorPositionDC/TestDF.java) — the executable
  specification for the data-class system. Use for: verifying intended behaviour
  (`isComplete`, `putWhenComplete`, `getOrCalc`, `setOuters`) when prose is
  ambiguous. This is the only test suite in the repo.
- [`src/main/resources/static/visur.js`](src/main/resources/static/visur.js) +
  [`visur.html`](src/main/resources/static/visur.html) — the production
  browser client (Canvas 2D, native `WebSocket` on `/ws`, bus envelope in
  both directions). Use for: Lesson 9 and any server↔browser question.
- [`frontEnd/editingScreen.js`](frontEnd/editingScreen.js) +
  [`editingScreen.html`](frontEnd/editingScreen.html) — a standalone prototype
  (h/j/k/l navigation, no EventBus, not wired to the server). Use for: comparison
  only; do not mistake it for the live frontend.
- [`build.gradle.kts`](build.gradle.kts) + [`settings.gradle.kts`](settings.gradle.kts)
  — Vert.x 4.4.4, Shadow fat jar, Java 16, main class `com.ple.visur.MainVerticle`,
  port 8888, `./gradlew fatJar` then `java -jar`. Use for: build/run questions.
- [`README.adoc`](README.adoc) — the author's own inventory of features (quantums,
  modes/submodes, words, commands, operators, spans, search, scope) plus the
  "Future Visur Features" list. Best single-page statement of *intent*.
- [`todo`](todo) — the author's active direction: `FileQuantum` /
  `DirectoryQuantum`, filesystem navigation, a `FilesystemView`. Use for: what is
  planned and what not to expect to find.
- [`wiki/overview.md`](wiki/overview.md) + [`wiki/pages/index.md`](wiki/pages/index.md)
  — the map into the source (35 pages, one per concept). Use for: finding where a
  topic lives. **Verify against source**: the wiki is generated and has known
  errors (e.g. the `canvas-cell` page points at a nonexistent `CanvasCell.java`;
  `line-wrapping` is described as a service when it is a two-value enum).

### Internal — course output

- [`lessons/itinerary.md`](lessons/itinerary.md) — the 10-lesson plan. Use for
  scope and sequencing; several bullets mis-frame the code, so trust source over
  it (see
  [`learning-records/0002`](learning-records/0002-data-class-and-cursor-model-corrections.md)).
- [`lessons/`](lessons/) — the lessons themselves (each ends with its own
  primary-source reading list).
- [`reference/`](reference/) — compressed cheat sheets:
  [`data-class-map.html`](reference/data-class-map.html),
  [`quantum-map.html`](reference/quantum-map.html),
  [`mode-submode-map.html`](reference/mode-submode-map.html),
  [`operator-catalog.html`](reference/operator-catalog.html),
  [`cursor-coordinates-map.html`](reference/cursor-coordinates-map.html),
  [`view-protocol-map.html`](reference/view-protocol-map.html) (bus addresses +
  16-field snapshot, Lesson 9),
  [`variable-map.html`](reference/variable-map.html) (the 10-name global variable
  map + command-sentence stack cheat sheet, Lesson 10).
- [`assets/`](assets/) — reusable lesson components: `styles.css` (shared
  Tufte sheet, every lesson links it), `quiz.js` (retrieval practice),
  `coord-lab.js` (Lesson 8), `protocol-trace.js` (Lesson 9; reusable for any
  message-sequence lesson), `var-lab.js` (Lesson 10; reusable command-sentence /
  execution-stack tracer for any future lesson on the command language).
- [`MISSION.md`](MISSION.md) and [`learning-records/`](learning-records/) — the
  goal and the decision-grade lessons that steer future sessions.

### External — framework and analogues

- [Vert.x 4 documentation](https://vertx.io/docs/) — verticles, event bus, service
  proxies, sockJS bridge. Use for the framework underneath Visur, not for Visur's
  design.
- Vert.x 4.4.4 source, for behaviour Visur depends on and never states:
  [`EventBusImpl`](https://github.com/eclipse-vertx/vert.x/blob/4.4.4/src/main/java/io/vertx/core/eventbus/impl/EventBusImpl.java)
  (`send` is point-to-point; one handler chosen round-robin — Lesson 9 §4) and
  `publish` versus `send` (a published message reaches every consumer, a sent
  one exactly one — the difference ticket
  [02](docs/wayfinder/files-and-registers/issues/02-viewport-per-client.md) hangs per-client views on). Use for: why one
  model change can reach every client. The SockJS bridge this used to cite is
  gone: the browser speaks `/ws` now.
- [Vim documentation](https://vimhelp.org/) — `motion.txt` and `change.txt`
  (movements, text objects, registers, search), plus `options.txt` for
  `'virtualedit'` and wrapping. Use for: the behaviour Visur imitates (desired
  column kept across vertical moves, text-object-like quantums).
- [Stack machine](https://en.wikipedia.org/wiki/Stack_machine) — the framing used
  in Lesson 10 for Visur's command language: a postfix evaluator whose named
  slots happen to persist between keystrokes. Use for: reading a command
  sentence as push/pop/assign, not as prose.
- [Relational model](https://en.wikipedia.org/wiki/Relational_model) and
  [database index](https://en.wikipedia.org/wiki/Database_index) — the analogue
  used in Lessons 7–8 for "one canonical value, derived views materialised on
  read".
- [Gradle Shadow plugin](https://github.com/GradleUp/shadow) — fat-jar packaging
  used by `./gradlew fatJar`.

## Wisdom (Communities)

- [github.com/jeremygurr/visur](https://github.com/jeremygurr/visur) — the repo
  and its issues. The author is the highest-signal source for design intent the
  code does not state; prefer asking there over guessing.
- [Vert.x community](https://vertx.io/community/) — forum, Stack Overflow
  (`vert.x` tag), and chat. Use for framework-level problems only.
- [Vi and Vim Stack Exchange](https://vi.stackexchange.com/) and
  [r/vim](https://reddit.com/r/vim) — for behaviour/feature comparisons against
  real Vim.

No community preference has been recorded yet. If the learner does not want to
join public communities, note it here and lean on the repo's own issues instead.

## Gaps

- No generated API/Javadoc reference; the source is the only reference.
- The wiki is auto-generated and is not always faithful to the source — always
  cross-check the named file.
- No protocol or architecture document for the event bus beyond the source; the
  sequence diagrams in the lessons are the current best description.
- No `CONTRIBUTING` or onboarding doc other than `README.adoc` and `todo`.
- No external article, paper, or talk describes Visur's architecture.
