# Terminal input and output from an out-of-JVM client

Type: research
Status: closed
Closed: 2026-10-28

## Question

What does a non-JVM client need in order to take over the terminal — paint to it, read keys from it — and how much of Visur's existing key handling turns out to be shaped by the browser rather than by the command language?

Unblocked: the language landed — **Rust + crossterm**, minimal tier ([Decide the terminal client's implementation language and TUI stack](04-decide-client-language.md)). Answer all three parts in Rust, against the component design in [Terminal client: components, responsibilities, and the server contract](../design/terminal-client-components.md), which fixes which of these jobs belong to crossterm (decode, raw mode, `SIGWINCH`) and which belong to us (adapter, emission, paint policy).

**A. Output.** Cursor addressing and painting are escape sequences on the TTY. Determine: how to avoid a full-screen flicker on repaint (does the chosen stack expose `DEC 2026` synchronized output, or suppress it?); how to restore the terminal on a panic, an uncaught error, or `SIGINT`; whether stdout buffering needs to be bypassed; and whether the alternate screen (`1049`) should be entered at all — the older ticket draft assumed `System.out`, and that assumption is gone with the JVM.

**B. Input, and the browser-shaped assumption.** This is the part most likely to bite, and it did not get easier by leaving Java.

The browser supplies [`event.key`](../../../../src/main/resources/static/visur.js#L132-134) values — `"ArrowLeft"`, `"Escape"`, `"Backspace"`, single characters — which [`KeyPressed.from(key)`](../../../../src/main/java/com/ple/visur/KeyPressed.java) maps into a `KeyPressed`, which [`Keymap`](../../../../src/main/java/com/ple/visur/Keymap.java) resolves to a `VisurCommand`. A terminal delivers **byte sequences** — `\x1b[A` for up, a lone `\x1b` for Escape (ambiguous with the start of every other escape sequence), `\x7f` for backspace.

Determine and report:

- Read [`KeyPressed.java`](../../../../src/main/java/com/ple/visur/KeyPressed.java), [`Keymap.java`](../../../../src/main/java/com/ple/visur/Keymap.java), [`KeymapMap.java`](../../../../src/main/java/com/ple/visur/KeymapMap.java) and [`InitializerService.java:168-171`](../../../../src/main/java/com/ple/visur/InitializerService.java#L168-171). What exact vocabulary must a terminal decoder emit so that the keymap resolves correctly? Enumerate it.
- Which of those tokens are browser artifacts that a terminal cannot produce (e.g. anything relying on `event.key` names the browser invented), and which are genuine parts of Visur's command language.
- How the lone-ESC ambiguity is conventionally resolved — timeout value, or lookahead — and what that means for a user typing Escape.
- What decodes modifier combinations (`Ctrl-`, `Alt-`), and whether Visur's keymap has vocabulary for modifiers at all today.
- Whether the client should normalise terminal bytes into the *current* `KeyPressed` vocabulary (cheap, `Keymap` untouched, but the browser-flavoured names become a wire format) or whether the vocabulary should be redesigned (honest, touches `Keymap` / `KeymapMap` / `InitializerService`). Recommend one; the shape ticket decides.

**C. Raw mode on macOS**, in the chosen language: how echo and line buffering are disabled, what happens to `SIGWINCH`, and what is lost relative to a real pty if the client is run under `tmux` or a wrapper.

The answer records: a recommended approach for input and output in the chosen stack, the complete terminal-bytes → key-token mapping table, and a list of concrete places where browser-shaped assumptions will have to change. That mapping table is what the spec's key section is written from.

## Answer

Answered 2026-10-28. Full write-up, mapping table, and citations: **[Terminal input and output from an out-of-JVM client: byte→token mapping, raw mode, and what outlives the browser](../research/terminal-input-mapping.md)**.

**The vocabulary is far smaller than the ticket feared, and nothing in it is unreachable.** `KeyPressed` is a bare `String`; the entire keymap system consumes exactly **seven fixed names** — `Escape`, `Enter`, `Backspace`, `ArrowLeft`, `ArrowRight`, `ArrowUp`, `ArrowDown` — **plus "any single-character string"**, which is the contract behind insert, replace, search targets, span-submode digits, the `q w e r` quantum keys, and command-line typing (`key.length() == 1` in the handlers). Everything else falls out of `Keymap.get` as `null` and is silently ignored.

**Browser artifacts are the spelling, not the keys.** All seven names are keys a terminal can produce, so *no token has to be deleted*. What is genuinely browser-shaped is (a) the DOM TitleCase spelling (a trap: `KeyCode::Esc` is not `Escape`) and (b) the fact that `event.key` collapses "which key" with "what it types" — the browser never had to distinguish `Ctrl-d` from `d`, because it drops modifiers into `event.ctrlKey`/`altKey` and the client never sends those fields. A terminal decoder *does* see `Char('d')` + `CONTROL`, so **a naive adapter turns `Ctrl-d`/`Alt-d` into the navigate binding `d` = `deleteCursorQuantum`.** That is the one place this bites, and it is fixed in the adapter, not the keymap.

**Recommendation on the shape question: normalise to the current vocabulary; do not redesign it now.** It needs no new names for any terminal key, and the honest option stays open without breaking the wire (`{"key": ..., "mods": [...]}`, absent `mods` = `[]`, added the day the first `Ctrl-` binding exists). The adapter must drop non-`NONE` modifiers rather than forward them, so today's accidental silence becomes deliberate.

**Lone `ESC`.** Crossterm resolves it in its unix parser with a short internal timeout before emitting `KeyCode::Esc`; the exact constant could not be verified in this run (search-only tooling, constant is private) and the research doc refuses to invent it — it specifies the measurement that brackets it and the source file to read before a number enters the spec. The better answer is to sidestep it: pushing kitty's `DISAMBIGUATE_ESCAPE_CODES` (`CSI >1u`, `PushKeyboardEnhancementFlags`) makes `Esc` arrive as the self-delimiting `CSI 27 u`, so no timeout is involved. Treat that as best-effort — **tmux traditionally swallows the push**, which is why tmux users are the ones who feel a ~50 ms Esc.

**Output.** `?1049` yes; `?2026` yes but *probed*, never assumed (crossterm exposes `BeginSynchronizedUpdate`/`EndSynchronizedUpdate`); restore is not RAII in crossterm, so a `Drop` guard *plus* a `panic::set_hook` must run `?2026l → ?25h → disable paste → pop keyboard flags → ?1049l → disable_raw_mode()`; write one `write(2)` per frame straight to fd 1, bypassing `std::io::Stdout`'s `LineWriter`.

**Raw mode changes a design assumption.** `enable_raw_mode()` also clears `ISIG`, so **Ctrl-C is a byte (`0x03`), not a `SIGINT`** — the "restore on `SIGINT`" line the ticket inherited is essentially unreachable from the keyboard, and the client must own Ctrl-C as an explicit quit/detach action. `SIGWINCH` still works (crossterm → `Event::Resize`), but under tmux it is *pane*-shaped and one hop late; what else tmux costs — `?2026` on tmux ≤3.4, the kitty push, your prefix key, and "probing reports tmux's capabilities, not the outer terminal's" — is tabulated in the research doc.

**What this implies for later tickets**

- **[08 — shape, transport, parity](08-decide-terminal-shape.md):** owns two decisions this ticket deliberately left open — whether a paste (`Event::Paste(String)`, which is *not* a `KeyEvent`) becomes one `insertChar`-shaped command or a dedicated message, and the attach-time capability probe for `?2026` + kitty DISAMBIGUATE (probe, do not version-check). It also inherits a measured-once latency note for tmux.
- **[03 — view-update protocol](03-decide-view-protocol.md):** the `{"key": ...}` envelope survives contact with the terminal; the only forward-compatible extension it needs is an optional `mods` array, so the payload shape need not change for keys.
- **[Terminal client: components](../design/terminal-client-components.md), when the spec is written:** three corrections to restate — component 2's input type is `Event`, not `KeyEvent` (paste); "collapses `Repeat`" must mean *forward one token per auto-repeat*, not one token per held key; and the `≤ 0.1 ms` decode budget holds only on the DISAMBIGUATE path, since the ESC-timeout fallback cannot meet it.
- **Two server-side gaps recorded, not fixed here:** `CommandStateService` handles `ArrowLeft`/`ArrowRight` but not Up/Down, and **no keybinding anywhere sets `isInCommandState = true`**, so command state is unreachable by keypress today. The spec needs a deliberate binding; a terminal client will faithfully deliver keys that do nothing.
