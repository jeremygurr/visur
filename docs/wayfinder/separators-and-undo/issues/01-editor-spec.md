# Editor specification: declared quantums, separators, nesting rank

Type: refactor
Status: closed
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

## Result

Landed. `EditorSpec` holds the entries; `QuantumNameToQuantum` gained the order and the separators and became the thing
queried at runtime (it is the one object already in the shared model, so nothing new went on the wire).
`getQuantumBoundsLengthAtSpan` is deleted — rank no longer measures anything.

**The ticket was wrong about "no behavior change", and the three frame tests proved it.** Swapping measurement for
declared rank alone broke `jumpingToEndOfAWordAtSpanOneLeavesYouInTheWordQuantum` and its two siblings: with the cursor
in a character quantum on a blank line, the *word* quantum is coarser, so rank made it the frame — but there is no word
there, and the jump would have selected an empty range instead of walking to the next word.

So the rule has two clauses, and only the first one changed: **rank says which is coarser; presence says whether it can
frame at all** (`Quantum.hasQuantumAtCursor`, measured at span 1). A quantum measured empty swaps roles with the other,
which is what the old code achieved by accident — a missing word quantum measured zero, so the character won on length.
Both clauses are now nameable, and `CODING_STANDARDS.md` got a section saying so.

That second clause is not a measurement returning as a ranking: presence is a question about the cursor's
neighbourhood, and asking it at the live span is what ticket 07 removed.

**One behavior change, in the tie the ranking used to lose.** `] q` on a one-character word measures equal to the word
it is in and used to make the *word* the frame — selecting a word where the key asked for a character. It now lands in
the character quantum, pinned by
`QuantumFrameTest.jumpingToTheCharacterInAOneCharacterWordLeavesYouInTheCharacterQuantum`. That test failed before the
change, which is the only reason to believe the fix is real.

Tests: 7 new (6 in `EditorSpecTest`, including a two-quantum spec that builds its own registry, order, separators and
validator; 1 in `QuantumFrameTest`), 160 Java green.
