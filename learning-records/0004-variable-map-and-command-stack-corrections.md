# Lesson 10 replaced the itinerary's "three variable categories / registers" model, and found two latent defects

Lesson 10 completes the course. The itinerary's framing of the variable system is
wrong in the same way Lessons 7–9 were, and this pass surfaced two concrete
defects worth remembering. Future sessions must use the corrected model.

1. **Two `VisurVar` implementations, not three categories.** Only
   [`ObjectVisurVar`](../src/main/java/com/ple/visur/ObjectVisurVar.java) (a box
   for any Java object) and
   [`BrickVisurVar`](../src/main/java/com/ple/visur/BrickVisurVar.java) (a live
   window onto a `PrimitiveDataClassBrick`) exist. The itinerary's "primitives
   (numbers), objects, bricks" is false: integers are `ObjectVisurVar` values.
   The real distinction is `BrickVisurVar` is *calculated* (`getVal()` calls
   `brick.getOrCalc()` when incomplete) while `ObjectVisurVar` is literal.
2. **"Registers" are just global variables named by convention.** There is no
   register file. The `i` key parks `span`/`cursorQuantum` in `tempSpan`/
   `tempCursorQuantum`; `Escape` restores and deletes them. Because the
   execution-data stack does not survive between keystrokes, the map is the only
   place a value can persist across a keystroke.
3. **The map is a stack-machine's named slots.** Commands are postfix programs
   against `ExecutionDataStack`: literals/numbers push, `"source" "target" ->`
   copies one variable into another (`AssignmentOp` pops target then source),
   a bare global name is a `RecallWord` that pushes its value, and
   `removeGlobalVar` pops a name and deletes it. `RecallWord` is also the
   silent sentence terminator for unknown tokens.
4. **`nl` is the read-only brick shared by both coordinate directions**, created
   incomplete and read-only in
   [`InitializerService`](../src/main/java/com/ple/visur/InitializerService.java#L125-138)
   and filled by `updateNextLineIndices`. This is the concrete bridge between
   the variable lesson and Lessons 7–8.
5. **The browser never sees the map.** The itinerary's "how the frontend reads
   and displays global state" is false. `visur.js` has no variable references;
   `ModelWasChangedVerticle.toJson()` *copies* five fields (ca, span,
   cursorQuantum, scopeQuantum, searchTarget) out of variables.

**Two latent defects found (verified from source, not yet runtime-confirmed):**

- `AssignmentOp` has an unexercised `BrickVisurVar` branch that casts
  `getVal()` — a raw value — to `PrimitiveDataClassBrick`
  ([AssignmentOp.java:21–22](../src/main/java/com/ple/visur/AssignmentOp.java#L21-22)).
  No keymap assigns *from* a brick variable, so it never runs; assigning
  `"nl"` or `"ca"` would throw `ClassCastException`.
- The `i` sentence leaves one `"character"` on the execution-data stack because
  `QuantumStartOp` ignores the stack
  ([QuantumStartOp.java:8–13](../src/main/java/com/ple/visur/QuantumStartOp.java#L8-13)).
  It leaks one entry per `i` press.

**Status:** active.

**Evidence:** source review behind
[`lessons/0010-variables-registers-and-global-state.html`](../lessons/0010-variables-registers-and-global-state.html)
and its companion
[`reference/variable-map.html`](../reference/variable-map.html), with a new
reusable component
[`assets/var-lab.js`](../assets/var-lab.js) (a command-sentence / stack tracer).
The widget's four sentences were simulated against the real token semantics and
produce the documented stacks. Not yet verified by learner exercises.

**Implications:** the "trust the source over the itinerary, then check the
framework" pattern now has four hits (LR-0002, LR-0003, this record). It should
be applied pre-emptively to any remaining itinerary claim. With Lessons 1–10
complete, the natural next assignment is the mission's own exercise: add a global
variable, copy it into `toJson()`, render it in the footer, and verify the
round trip. `assets/var-lab.js` is reusable for any future lesson about the
command language or the evaluation stack.
