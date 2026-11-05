# Editor specification: declared quantums, separators, nesting rank

Type: refactor
Status: open
Blocked by: —

## Work

Make Visur's four quantums **data belonging to an editor specification** instead of four classes registered by hand
in one method.

Today, three separate places assume a plaintext document:

1. [`InitializerService.initializeQuantums`](../../../../src/main/java/com/ple/visur/InitializerService.java) —
   `QuantumNameToQuantum` gets `word`, `character`, `line`, `document` hardcoded, with `"\\S+"` inline as the word
   pattern.
2. `KeyToQuantumName` maps `q`/`w`/`e`/`r` to those names, and nothing checks the names exist.
3. **Coarseness is measured, not declared** —
   [`Quantum.getFrameAndCursorQuantums`](../../../../src/main/java/com/ple/visur/Quantum.java) compares
   `getQuantumBoundsLengthAtSpan(1)` of two quanta and lets the longer win. That is a guess at nesting order made from
   lengths.

Introduce `EditorSpec`:

- a **name** (`plaintext`);
- an **ordered list** of quantum entries, ordered from finest to coarsest — `character`, `word`, `line`, `document`;
- each entry carries the **quantum** and its **separator characters** — `character` none, `word` space and tab, `line`
  one newline, `document` none;
- the **nesting rank** is the entry's index. `getFrameAndCursorQuantums` compares `rankOf(...)` instead of measuring
  two bound lengths.

Register one spec — `plaintext` — at boot, chosen once (there is no document-type detection in the codebase; `loadFile`
never looks at the extension). `QuantumNameToQuantum` is built from the spec rather than written out, and
`KeyToQuantumName` is **validated against it at boot**: a key naming a quantum the spec does not have is a startup
error, not a dead key press at three in the morning.

Do **not** put keybindings in the spec. `q` / `w` / `e` / `r` stay in `KeyToQuantumName`; keymaps are the next map's
subject.

**No behavior may change.** This ticket moves where a fact is written down, not what the fact is.

## Done when

- The four quantums are registered from the spec, and a test builds a **fake two-quantum spec** and shows the registry
  and the rank lookup work with it — otherwise "the seam is real" is a claim about one instance of four.
- Every frame test from tickets 02 and 07 still passes unchanged. These are the acceptance criteria: the declared rank
  must pick the same quantum that the measurement picked, in every case, including
  `twentyWordsOutMeasureTwentyLinesAndTheLineStillFrames`.
- Each quantum reports its separators, and a test asserts `plaintext`'s four: character none, word space+tab, line
  newline, document none.
- Boot fails loudly on a keymap naming a missing quantum (test the failure, not just the happy path).
- The word pattern `"\\S+"` lives with the `word` entry in the spec, not in `initializeQuantums`.
- [ ] Manual (user-driven) check: server starts, `[` / `]` behave identically, nothing on screen differs.

## Notes

- This is the ticket where `codebase-design` earns its place: a spec with exactly one instance is the shape most
  likely to be a wrapper around four hardcoded classes. The fake-spec test is the check that it is not.
- The separator data is **read by nothing** until [02](02-selection-includes-separator.md). Landing them separately is
  deliberate: 01 is provably behavior-preserving, so if 02 misbehaves the separator *rule* is the suspect, not the
  plumbing.
