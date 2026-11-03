# Count prefix in navigate mode; delete the `s`-then-digit submode

Type: task
Status: closed
Closed: 2026-11-03
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
- Delete the old path outright: `SpanSubmodeHandler` (deleted),
  `initializeSpanKeymap`, the `EditorSubmode.span` registration, and the `EditorSubmode.span` constant. Check every
  read of the enum first (`View.editorSubmode`, `KeymapMap.get`, the terminal's `editorSubmode` string comparison) so
  nothing asks for a keymap that no longer exists.
- Show the pending count on the mode line while it is pending — a typed `12` with nothing on screen is indistinguishable
  from a dead key. Coordinate with [ticket 10](10-mode-line-scope.md), which owns that line.
  **Left to 10**, see the result.

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

## Result

`12 s` sets span 12; `s` sets 1; `0 s` collapses; a digit after `s` starts the next count rather than revising the
last key. All four are pinned by [`CountPrefixTest`](../../../../src/test/java/com/ple/visur/CountPrefixTest.java),
which drives them through [`KeyWasPressedVerticle.press`](../../../../src/main/java/com/ple/visur/KeyWasPressedVerticle.java)
— the real keymap and the real compiler — because the ticket's own acceptance is a sequence of keypresses. Three of
them fail the moment `SetSpanOp` stops reading the count, so they test the path and not the plumbing.

The old path is gone: `SpanSubmodeHandler`, `initializeSpanKeymap`, the `EditorSubmode.span` registration and the
constant. Neither renderer named the submode (they print whatever `editorSubmode` arrives), so nothing had to change
there, and both already forward digits as ordinary characters. `getSubmodeByString("span")` returning null is asserted
so the deletion cannot silently half-happen again.

### The handler was not where this could live

The ticket asked for a `KeymapHandler` on the navigate keymap. Handlers cannot honour the ticket's own clearing rule:
[`Keymap.get`](../../../../src/main/java/com/ple/visur/Keymap.java) tries the bound keys **first** and only falls
through to handlers on a miss, so `h`, `s`, `i` and every other bound key would have skipped a clearing handler.
So [`CountPrefix`](../../../../src/main/java/com/ple/visur/CountPrefix.java) is a static called from the one place
every keypress passes through, and the rule is two calls around the keypress:

- `record` before the keymap: a lone digit in navigate mode folds into the count and consumes the keypress;
- `retireUnlessDigit` after it, in a `finally`, so a count dies with the next keypress whether or not that keypress
  matched anything, and `Escape` needs no special case.

`press` became static for the same reason the `gg`/`G` state was split out beside it into `documentJumpConsumed`: a
verticle cannot be constructed outside a Vert.x context ([`AbstractVisurVerticle`](../../../../src/main/java/com/ple/visur/AbstractVisurVerticle.java)
reads the current context in its constructor), and the precedent for "static so it can be tested" is
`enterCommandStateIfRequested`. Splitting the jump out also means [ticket 09](09-remove-gg-and-capg.md) deletes one
method. Incidentally the jump paths used to `send(modelWasChanged)` and then fall into the sender at the end; now one
keypress sends exactly one.

### Two sources for the span, ranked

`i`, `a` and `o` still compile `0 setSpan` into their bindings, so `SetSpanOp` takes an operand when one is on the
stack and the count otherwise. A binding that names the span outranks a stray count, which is what a mistyped
`3 i` should mean; the precedence is one comment at
[`SetSpanOp.spanRequested`](../../../../src/main/java/com/ple/visur/SetSpanOp.java).

### What is not done

**A pending count is invisible.** The mode line is [ticket 10](10-mode-line-scope.md)'s, and it will add the field to
[`View`](../../../../src/main/java/com/ple/visur/View.java) once rather than twice; the state is already in the model
(`getPendingCount`), so 10 is display-only. Until then a typed `12` shows nothing until `s` lands, which is the
known cost of the split.
