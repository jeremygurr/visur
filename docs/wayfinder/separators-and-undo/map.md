# Map: separators-and-undo

## Destination

A **selection covers whole units, separator included**, and **every command can be taken back**.

Concretely: `s` at the line quantum highlights the line *and the newline that ends it*, so `d` leaves no stray blank
line and the register holds something `p` can put back on its own line. `p` pastes at the selection's start, replacing
what the selection covers and taking what it displaced into the register, while `P` puts the same text in front of the
selection without disturbing it. `g` and `G` open a line above and below and drop you into insert mode, and a count
there — `3 g` — types once and repeats what you typed, the way vim's `3o` does. `u` and `R` walk the document back and
forward.

**All six tickets are closed** as of 2026-11-09.

Underneath all of it: **Visur's four quantums stop being hardcoded.** They become the content of an **editor
specification** — a declared list of quantums, each with its separator, and the nesting order between them. `plaintext`
is the first specification and reproduces exactly today's four quantums; a `json` specification, with key-value quantums
and comma separators, is the reason the layer exists and the next map's first ticket.

This closes the open question [ticket 12](../span-and-count/issues/12-count-on-operators.md) left behind ("deleting N
lines leaves a newline behind"), the [span-and-count fog](../span-and-count/map.md) entry on **undo**, and the oldest
structural assumption in the model.

## Notes

- **Feature 1 of this batch already shipped.** `d` yanks what it removes — `ac5bd97`, pinned by
  `OperatorCountTest.deletingOverwritesWhatWasYanked`, which reconstructs the document exactly from
  `y` → `d` → `p`. It is a check to re-run, not a ticket.
- **`getBoundaries` is abstract in all four quantums**, so "selections include their separator" has no shared home
  today. [Ticket 01](issues/01-editor-spec.md) makes it a `final` template method over the per-class work, which is
  also the natural place for a rule that is true of every quantum.
- **`boundsFor` statics stay pure.** Ticket 01 of the previous map put a five-argument pure seam under every quantum
  and pinned it with 40-odd tests. The separator rule does not get a sixth argument: it is applied *after* the
  per-class answer, by a pure `Separators.including(content, bounds, separators)` that gets its own tests.
- **`TextInsertion.insertAt` re-seats bounds at span 0.** That is right for `p` (a paste leaves no selection) and wrong
  for `P` (which must not erase a highlight it did not touch), so [ticket 03](issues/03-paste-replaces-selection.md)
  adds the second insertion path rather than a flag on the first.
- **The count dies at Escape.** `press()` runs the keymap before `retireUnlessDigit`, so Escape's `"navigate" changeMode`
  has already landed when the count is retired. Every command that enters insert mode therefore stashes the count with
  the span and cursor quantum it already stashes (`InitializerService:472`) — the insert repeat cannot read it later.
- **Ranking becomes declared.** Coarseness is *measured* today (`getQuantumBoundsLengthAtSpan(1)`, longer wins —
  [ticket 07](../span-and-count/issues/07-frame-at-span-one.md)). A spec with an ordered quantum list can simply say.
  The five frame tests from tickets 02 and 07 are the acceptance criteria for that swap: same quantum must win in
  every case.
- **There is no document type in the codebase.** `loadFile` takes a path and nothing reads its extension, so the spec
  is chosen once at boot. Extension-based selection is the seam this map leaves for the JSON editor, not something it
  builds.
- **Skills to consult**: `tdd` throughout (ticket 01 and 02 are pure arithmetic at an existing seam; 03–06 need the
  live model), `codebase-design` for ticket 01 — a spec whose only consumer is one hardcoded four-item list is the
  interface test that skill exists for. Doc care in 02, 03 and 06, since `keys.md`, the conformance row and the view
  contract all change.

## Decisions

<!-- settled in the grilling session of 2026-11-04, before any ticket was written -->

- **Selections include the following separator by default**, and the highlight shows it: a line selection covers one
  cell past the end of the row, standing for the newline. **The whole run**, not one character: `2 s` on
  `"one   two   three"` is `"one   two   "`.
- **Word separators are space and tab; never newline.** A word selection at the end of a line therefore takes nothing.
  Newlines already inside the selection stay inside it. Line separator is one newline; character and document have none.
- **`\` toggles separator inclusion**, sticky for the session (a preference about what selection *means* should not
  flip when a file loads), shown as `SEP=ON|OFF` placed **left of `SPAN=`** so the right-clipping mode line cannot
  lose it. With `SEP=OFF` the bounds and the highlight both shrink.
- **"Include the following separator" means "include it if it exists."** The last line of a document has no terminator,
  and nothing is ever synthesised.
- **The separator extends the far end of the extent only** — one cell, not one per unit — and applies only when the
  range is non-empty. Span 0 stays a collapsed range so `p` still inserts at the caret.
- **`EditorSpec` declares quantums in nesting order, each with a separator**, and nothing else. Keymaps keep the
  `q`/`w`/`e`/`r` → name mapping (`KeyToQuantumName`), so keybinding changes stay keybinding changes. **Boot validates
  the keymap against the spec** and fails loudly rather than leaving a dead key.
- **A delete leaves a gap, not a window.** The span survives `d`, but the range collapses to where the text was: a
  window re-derived over the neighbours would be a highlight nobody selected, and `p` replaces the selection. Detail in
  [ticket 03](issues/03-paste-replaces-selection.md).
- **`p` and `P` both insert at `cursorQuantumStart`.** `p` captures the selection, deletes it, pastes there, and makes
  what it displaced the new register contents. `P` inserts there and **leaves the selection covering the same text,
  shifted right by the pasted length** — it is non-destructive by definition, and quantum-independent.
- **The register records what this keypress removed, and only then.** Over nothing, the register is unchanged, so
  `p p` with no selection pastes twice rather than once and then nothing. `3 p` pastes three copies and records the
  displaced text once.
- **`g` / `G` are line-based whatever the cursor quantum**: `g` inserts `"\n"` at the enclosing line's start, `G` at
  its end, and the caret lands on the new empty line. A key named "open a line" opening a line only sometimes is worse
  than one that ignores the current quantum.
- **Insert-entering commands stash the count** and, **when it is above 1**, record the session's typed text. On
  Escape, that text is repeated `count - 1` more times at the caret: `g`/`G` separate copies with the newline they
  opened with, `i`/`a` separate them with nothing. An empty session repeats the newline, so `3 g<Esc>` is three blank
  lines. Caret ends on the last copy, exactly as if you had typed it there `count` times.
- **Counting keeps four meanings**, all vim's: multiply a span (`3 s`), repeat a motion or jump (`3 l`, `3 ] e`),
  multiply an operator's extent (`3 d`), repeat an insert session (`3 g`). Documented as four readings of one number
  in one row of `keys.md`, rather than unified into a fifth rule.
- **Undo's unit is a command**, its boundary is **entering navigate mode** — so `g`, the typing and the repeat are one
  unit, and `3 d` is one unit. **Only content-changing commands snapshot**, so `u` after `5 l` does nothing rather than
  appearing broken.
- **Undo restores content, cursor and span — never the register.** Accepted asymmetry: undoing a paste leaves the
  register holding the text that paste displaced.
- **Snapshots, not inverse operations**: a ring of `(content, cursorQuantumStart/End, span, ca)` around 200 deep, and a
  second stack for redo. A 5 KB string per command is nothing beside the render cost; an inverse per operator is one
  chance per operator to corrupt the document.
- **Redo is `R`, not `Ctrl-r`.** Revised after the first draft: `Ctrl-r` needs a new wire token, an exception to the
  terminal's deliberate "drop every chord" rule (the rule that stops `Ctrl-d` from deleting), and `preventDefault` in
  the browser or redo reloads the page — three client changes on top of the feature. `R` arrives through both clients
  today. Accepted collision: `R` is vim's replace mode, the same class as `o`, and keymaps are the next map's subject.

## Tickets

| # | title | blocked by |
|---|---|---|
| ~~01~~ [closed](issues/01-editor-spec.md) | Editor specification: declared quantums, separators, nesting rank | — |
| ~~02~~ [closed](issues/02-selection-includes-separator.md) | Selections include their separator; `\` toggle; highlight and mode line | 01 |
| ~~03~~ [closed](issues/03-paste-replaces-selection.md) | `p` replaces the selection and takes what it displaced; `P` inserts before it | 02 |
| ~~04~~ [closed](issues/04-open-line.md) | `g` / `G` open a line above / below at line bounds | 02 |
| ~~05~~ [closed](issues/05-insert-repeat.md) | A count repeats the insert session's text on Escape | 04 |
| ~~06~~ [closed](issues/06-undo-redo.md) | Undo and redo: snapshot ring, `u`, `R` | 02, 03 |

**01 → 02** is the spine: nothing in the batch behaves differently until 02 lands, because 01 is a pure refactor that
must reproduce every existing bound exactly. **03** and **04** are independent of each other and both read the register
02 produces. **05** extends 04's session. **06** comes last because undo must be able to take back the pastes 03
introduces — and because its unit boundary ("entering navigate mode") is a rule 05 depends on.

## Not yet specified

- **The JSON editor.** This map builds the specification layer and one instance of it. Which quantums a JSON document
  has, how a key-value quantum is bounded against nested braces, and how the spec is chosen from a file path are all
  the next map. The spec is deliberately chosen at boot so that choice is a plugin, not a rewrite.
- **Named registers.** Still clobbered unconditionally; now with a second way to clobber (`p` over a selection). The
  first user who pastes and wants the previous register back has found the ticket.
- **Undo across documents.** The ring is per model, so `loadFile` either clears it or leaves snapshots pointing at a
  document that is gone. Neither is designed here; the safe default (clear on load) is ticket 06's to state.
- **Insert-mode counts.** Digits typed *inside* insert mode are text, not counts. Only the count typed before the
  entering key is stashed.
- **`o` and `R`.** `o` is replace mode today and vim fingers want open-line; `R` becomes redo and vim fingers want
  replace mode. Both deliberately untouched beyond that: keymaps are the next map's subject by the user's instruction,
  and `R` is wanted now.

## Out of scope

- **Undoable movement or a jump list.** Undo only ever reverses content changes.
- **Coalescing by time window** (`u` merging two deletes typed quickly) — vim does not, and the unit is already a
  command.
- **Mouse, marks, visual-block mode, macro recording, multi-user editing.**
- **A general chord vocabulary.** `Ctrl-r` was dropped from scope along with the key; the terminal still drops every
  CONTROL/ALT/SUPER chord, and widening that is its own decision.
