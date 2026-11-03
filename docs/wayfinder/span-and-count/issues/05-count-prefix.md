# Count prefix in navigate mode; delete the `s`-then-digit submode

Type: task
Status: open
Blocked by: —

## Work

Counts go **before** the key — `12 s` — so a count is not capped at one digit, and so one mechanism serves every
command that takes one.

- Add a `CountPrefixHandler` (`KeymapHandler`, registered on the navigate keymap exactly like
  [`ChangeCursorQuantumHandler`](../../../../src/main/java/com/ple/visur/ChangeCursorQuantumHandler.java) is) that
  matches a digit and accumulates it into a pending count: `3` then `4` is 34. Return `null` from the handler when a
  digit is consumed so nothing else runs — the keymap's handler chain already behaves that way
  ([`Keymap.get`](../../../../src/main/java/com/ple/visur/Keymap.java)).
- Store the count where the model keeps such state — an `EditorModelKey` entry read/written through
  [`EditorModelCoupler`](../../../../src/main/java/com/ple/visur/EditorModelCoupler.java), not a Java field. It has to
  survive across keypresses and the terminal must be able to show it.
- **Any non-digit keypress consumes and clears it.** No `Escape`-specific cancellation: navigate has no `Escape`
  binding today and adding one just to cancel a number is a new way to be wrong.
- `s` compiles `"<count> setSpan"`. Bare `s` is count 1. `0 s` is 0 and collapses the selection.
  [`LiteralNumberWord`](../../../../src/main/java/com/ple/visur/LiteralNumberWord.java) already parses multi-digit
  integers, so the compiler needs no change.
- Delete the old path outright: [`SpanSubmodeHandler`](../../../../src/main/java/com/ple/visur/SpanSubmodeHandler.java),
  `initializeSpanKeymap`, the `EditorSubmode.span` registration, and the `EditorSubmode.span` constant. Check every
  read of the enum first (`View.editorSubmode`, `KeymapMap.get`, the terminal's `editorSubmode` string comparison) so
  nothing asks for a keymap that no longer exists.
- Show the pending count on the mode line while it is pending — a typed `12` with nothing on screen is indistinguishable
  from a dead key. Coordinate with [ticket 10](10-mode-line-scope.md), which owns that line.

## Amendments this ticket carries

- [terminal-input-mapping.md](../../renderer-delivery/research/terminal-input-mapping.md) — the
  `SpanSubmodeHandler` row and the "`0`–`9` → span submode" rows describe a handler that stops existing. The terminal
  itself needs no change: digits are already forwarded as ordinary characters.
- [docs/reference/keys.md](../../../reference/keys.md) — the `s` row and the In-flight section.

## Done when

- `12 s` selects twelve quanta; `3 s` selects three; `s` selects one; `0 s` collapses.
- `3 q` (or any other key after a digit) leaves no pending count behind, and behaves exactly as the bare key.
- `s` followed by a digit no longer sets a span — and, verified by test, does nothing surprising instead.
- No reference to a `span` submode remains in Java, the terminal, or the canvas.
