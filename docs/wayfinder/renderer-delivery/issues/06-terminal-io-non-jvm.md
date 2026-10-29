# Terminal input and output from an out-of-JVM client

Type: research
Status: open
Blocked by: 04

## Question

What does a non-JVM client need in order to take over the terminal — paint to it, read keys from it — and how much of Visur's existing key handling turns out to be shaped by the browser rather than by the command language?

Blocked by [Decide the terminal client's implementation language and TUI stack](04-decide-client-language.md), because half the answer is "reach for library X". Once the language lands, answer all three parts in it.

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
