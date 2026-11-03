# Terminal input and output from an out-of-JVM client: byte→token mapping, raw mode, and what outlives the browser

Research for [Terminal input and output from an out-of-JVM client](../issues/06-terminal-io-non-jvm.md).
Stack is fixed by [Decide the terminal client's implementation language and TUI stack](../issues/04-decide-client-language.md)
(Rust + crossterm, minimal tier) and the job split is fixed by
[Terminal client: components](../design/terminal-client-components.md) (crossterm owns decode, raw mode,
`SIGWINCH`; we own the grid, damage, and every escape byte). Nothing here re-opens either.

**Evidence quality up front.** This run had search only — no page fetch, and `source_check` was not
available — so crossterm internals are reported from doc/API pages and project docs seen through search
snippets, not from an opened source tree. Every claim that needs crossterm source to be settled is marked
**unverified** below with the exact check that settles it. The mapping table itself is on solid ground:
its Visur half is read directly from the repo, and its byte half is standard ANSI/DEC and
kitty-protocol material.

---

## Summary

Visur's on-the-wire key vocabulary turns out to be tiny and almost entirely terminal-friendly:
`KeyPressed` is a **bare `String`**, and the whole of `Keymap`/`KeymapMap`/`InitializerService` plus the
nine keymap handlers consume exactly **seven fixed names** (`Escape`, `Enter`, `Backspace`, `ArrowLeft`,
`ArrowRight`, `ArrowUp`, `ArrowDown`) **plus "any single-character string"** — the latter being how
insert, replace, search, span-submode digits, quantum keys, and command-state typing all work
(`key.length() == 1`). All seven names are keys a terminal *can* produce, so nothing in the vocabulary is
unreachable; what is browser-shaped is the **spelling** (DOM `event.key` TitleCase) and, more
dangerously, the fact that the browser never had to think about **modifiers or byte aliasing**. The
client must therefore normalise into today's vocabulary *and* filter on modifiers before extracting the
character, because `Ctrl-d` and `Alt-d` both arrive as `Char('d')` with a modifier — a naive adapter
turns them into the navigate binding `d` (`deleteCursorQuantum`) and destroys the user's line.

---

## Verdicts (the short version)

| Part | Verdict |
|---|---|
| **A. Output** | Alternate screen `?1049` **yes**; synchronized update `?2026` **yes, but probe it and never assume it** (crossterm exposes `BeginSynchronizedUpdate`/`EndSynchronizedUpdate`, so no hand-rolled fallback is needed — though we own the escape bytes anyway); restore through a `Drop` guard **plus** a `panic::set_hook`, because crossterm's raw mode is not RAII; write **one `write(2)` per frame to fd 1**, bypassing `std::io::Stdout`'s `LineWriter`. |
| **B. Input** | **Normalise to the current vocabulary.** Do not redesign it in this effort. The vocabulary is 7 names + any single char; it needs no change to carry every terminal key Visur can bind today. Add a modifier channel later only when a binding first needs one (§6). |
| **C. Raw mode** | `enable_raw_mode()` clears line buffering and echo and **also `ISIG`**, so Ctrl-C stops being a signal and becomes byte `0x03` — which Visur has no binding for, so the *client* must own "Ctrl-C exits". `SIGWINCH` survives under tmux but arrives one hop late and only for **pane** resizes. Under tmux you lose `?2026` on older tmux, the kitty keyboard push (so no disambiguated `Esc`), and your prefix key. |

---

## 1. The complete vocabulary `KeyPressed` accepts

`KeyPressed` is a string wrapper with `equals`/`hashCode` on the string
([KeyPressed.java](../../../../src/main/java/com/ple/visur/KeyPressed.java)); `KeyPressed.from(key)` does
no validation. `KeyWasPressedVerticle` builds one straight from the `"key"` field of the `keyWasPressed`
message and hands it to either the command-state handler or the current submode's `Keymap`
([KeyWasPressedVerticle.java](../../../../src/main/java/com/ple/visur/KeyWasPressedVerticle.java)).
`Keymap.get` is a `HashMap` lookup that falls through to `KeymapHandler[]` when the exact key misses
([Keymap.java](../../../../src/main/java/com/ple/visur/Keymap.java)).

So the vocabulary is the union of (a) explicit `keymap.put(KeyPressed.from(...))` literals and (b) what
the handlers accept on a miss. Enumerated from
[InitializerService.java](../../../../src/main/java/com/ple/visur/InitializerService.java) (`initializeKeymaps`
and its nine `initialize*Keymap` methods) and every handler:

**Explicit literals (18 distinct strings)**

| Keymap | Keys |
|---|---|
| `navigate` | `h` `l` `j` `k` `ArrowLeft` `ArrowRight` `ArrowDown` `ArrowUp` `[` `]` `s` `i` `a` `o` `d` `f` `F` `n` `N` `/` |
| `insert` | `Escape` `ArrowLeft` `ArrowRight` `ArrowUp` `ArrowDown` `Backspace` `Enter` |
| `replace` | `Escape` `ArrowLeft` `ArrowRight` `ArrowUp` `ArrowDown` `Backspace` |
| `quantumStart`, `quantumEnd`, `searchForward`, `searchBackward`, `changeScope` | none — handler-only |

**Handler vocabulary (the fall-through on a keymap miss)**

| Handler | Accepts | Effect |
|---|---|---|
| [InsertModeHandler](../../../../src/main/java/com/ple/visur/InsertModeHandler.java) | any `key.length() == 1` | `"<c>" insertChar 1 0 relativeMove` |
| [ReplaceModeHandler](../../../../src/main/java/com/ple/visur/ReplaceModeHandler.java) | any `length() == 1`, plus `Enter` | `replaceChar` / `0 1 relativeMove` |
| [ChangeCursorQuantumHandler](../../../../src/main/java/com/ple/visur/ChangeCursorQuantumHandler.java) | keys in `KeyToQuantumName` = `q` `w` `e` `r` | `"<quantum>" changeCursorQuantum` (note `e` = **line**, not vim's end-of-word) |
| [QuantumStartSubmodeHandler](../../../../src/main/java/com/ple/visur/QuantumStartSubmodeHandler.java) / [QuantumEndSubmodeHandler](../../../../src/main/java/com/ple/visur/QuantumEndSubmodeHandler.java) | `q` `w` `e` `r`, then `removeSubmode` regardless | `quantumStart` / `quantumEnd` |
| [CountPrefix](../../../../src/main/java/com/ple/visur/CountPrefix.java) — not a keymap handler: it runs in [KeyWasPressedVerticle](../../../../src/main/java/com/ple/visur/KeyWasPressedVerticle.java) *before* the keymap, which is how it sees a digit that no keymap would match | one digit `0`–`9` in navigate mode | accumulates the count for the next key: `12 s` is twelve quanta |
| [SearchForwardSubmodeHandler](../../../../src/main/java/com/ple/visur/SearchForwardSubmodeHandler.java) / [SearchBackwardSubmodeHandler](../../../../src/main/java/com/ple/visur/SearchBackwardSubmodeHandler.java) | any `length() == 1` (appended to the search target), `Enter`, `Escape`, `Backspace` | search / submit / abandon / rub out |
| [ChangeScopeSubmodeHandler](../../../../src/main/java/com/ple/visur/ChangeScopeSubmodeHandler.java) | `q` `w` `e` `r`, then `pushSubmode` regardless | `changeScope` |
| [CommandStateService](../../../../src/main/java/com/ple/visur/CommandStateService.java) (not a keymap) | `Escape`, `Enter`, `ArrowLeft`, `ArrowRight`, `Backspace`, any `length() == 1` | abort / compile+execute / move cursor / rub out / insert char |

**Conclusion (direct evidence + one inference).** The wire contract is exactly:

```
KeyToken := "Escape" | "Enter" | "Backspace"
          | "ArrowLeft" | "ArrowRight" | "ArrowUp" | "ArrowDown"
          | <any single-character string>
```

Nothing else is ever meaningful. `Keymap.get` returns `null` → `KeyWasPressedVerticle` executes nothing,
so longer strings are silently swallowed. *Inference:* this is also why the browser "works" despite
sending `event.key` verbatim ([visur.js:132-134](../../../../src/main/resources/static/visur.js#L132-L134)) —
auto-repeat, modifier-only keys, IME and `Dead` all produce strings of length ≠ 1 and vanish.

**A second consumer the ticket did not name:** `CommandStateService` branches on the same strings with
its own subset (`ArrowLeft`/`ArrowRight` only — **no Up/Down**, and `Enter` *commits* the command line).
The table below covers it as its own column. Note also that no keybinding in `InitializerService`
compiles anything that sets `isInCommandState = true` (checked every `scs.compile` literal in that file),
so command state is currently unreachable from a keypress — a gap for the spec, not a decision here.

---

## 2. The mapping table: Visur token ⇄ crossterm ⇄ terminal bytes

`mods` = `KeyModifiers`, which must be `NONE` for every row in the top half. Bytes are hex, with the
conventional `\x1b` spelling.

### 2a. Tokens a terminal client must be able to emit

| Visur token | Consumed by | crossterm `KeyCode` / mods | Bytes from the terminal | Notes |
|---|---|---|---|---|
| `Escape` | insert, replace, search submodes, command state | `Esc` / `NONE` | `1b` — kitty-disambiguated: `1b 5b 32 37 75` (`CSI 27 u`) | **The only ambiguous row.** See §4. Ctrl-[ is the same byte. |
| `Enter` | insert, replace handler, search submit, command-state commit | `Enter` / `NONE` | `0d` (CR) | Raw mode has ICRNL off, so Enter is CR; crossterm also maps `0a` (LF) to `Enter`. Ctrl-M is the same byte. |
| `Backspace` | insert, replace, search rub-out, command-state rub-out | `Backspace` / `NONE` | `7f` (DEL, the xterm convention) or `08` (BS on some terminals/consoles) | Both map to `Backspace`. `08` is also Ctrl-H — a terminal cannot tell them apart. |
| `ArrowLeft` | navigate, insert, replace, command-state cursor | `Left` / `NONE` | `1b 5b 44` (`CSI D`), or `1b 4f 44` (`SS3 D`) with DECCKM/application-cursor-keys on | Never send `smkx`; crossterm parses both forms regardless. |
| `ArrowRight` | same | `Right` / `NONE` | `1b 5b 43` / `1b 4f 43` | |
| `ArrowUp` | navigate, insert, replace | `Up` / `NONE` | `1b 5b 41` / `1b 4f 41` | |
| `ArrowDown` | navigate, insert, replace | `Down` / `NONE` | `1b 5b 42` / `1b 4f 42` | |
| `h` `l` `j` `k` `s` `i` `a` `o` `d` `f` `[` `]` `/` `F` `N` `n` | `navigate` literals | `Char(c)` / `NONE` | the UTF-8 bytes of `c`, e.g. `68`, `5b`, `2f` | `F`/`N` arrive as `Char('F')`/`Char('N')` from Shift+letter; Caps Lock produces the same byte. |
| `q` `w` `e` `r` | quantum keys under `navigate`, `quantumStart`, `quantumEnd`, `changeScope` | `Char(c)` / `NONE` | `71` `77` `65` `72` | |
| `0`–`9` | `span` submode | `Char(c)` / `NONE` | `30`–`39` | |
| *any single char* | insert, replace, search target, command-state typing | `Char(c)` / `NONE` | UTF-8 bytes of `c` (1–4 bytes) | The adapter's job is `c.to_string()` **only when `mods == NONE`**. |

### 2b. Bytes a terminal sends that Visur cannot express — **swallow, do not forward**

| Physical key | crossterm decodes as | Bytes | Why it must not reach the server |
|---|---|---|---|
| `Ctrl-a` … `Ctrl-z` | `Char('a'..)` + `CONTROL` | `01`–`1a` | Forwards as `"a"`… → fires the **navigate** binding. `Ctrl-d` would delete a quantum. |
| `Alt-<char>` | `Char(c)` + `ALT` | `1b` + bytes of `c` | Same trap; `Alt-d` = `deleteCursorQuantum`. Also why `Alt-[` is genuinely ambiguous. |
| `Tab` / `Ctrl-i` | `Tab` + `NONE` | `09` | Unbound; forwarding `"\t"` would inject a raw tab into an `insertChar "<c>"` literal. |
| `Ctrl-m` / `Ctrl-j` | `Enter` + `CONTROL` | `0d` / `0a` | Same byte as plain Enter. Decide once: treat as `Enter` (vim does) or drop. |
| `Ctrl-h` | `Backspace` + `CONTROL` | `08` | Same byte as Backspace. Treat as `Backspace`. |
| `Home` `End` `PgUp` `PgDn` `Ins` `Del` `F1`–`F12` | `Home`/`End`/… / `F(n)` | `1b[H`, `1b[F`, `1b[5~`, `1b[6~`, `1b[2~`, `1b[3~`, `1bOP`…`1b[24~` | Unbound in every keymap; `length() != 1` so the server ignores them anyway — but drop them client-side so the protocol stays honest. |
| Bracketed paste | `Event::Paste(String)` (not a `KeyEvent`) | `1b 5b 32 30 30 7e` … payload … `1b 5b 32 30 31 7e` | See §7. |
| Kitty / CSI-u modified keys | `Char` + mods, or `KeyCode` with mods | `1b 5b <code> ; <mods> u` | Same modifier-filter rule. |

**Rule the adapter encodes (recommendation):** if `mods` contains anything other than `SHIFT`, the event
is *not* a Visur token — drop it (and, for now, drop the whole class rather than map it to something
plausible). `SHIFT` alone needs no handling: it already shows up as the uppercase/`Char` variant, which is
exactly what the `F`/`N` bindings want.

---

## 3. Browser artifacts vs genuine command language

**Genuine command-language vocabulary** (keys the language asks for, independent of any UI):
`h l j k` (movement), `d` (delete quantum), `i a o` (enter insert/replace), `[` `]` (quantum start/end),
`s` (span), `/` (change scope), `f` `F` `n` `N` (search), digits (span count), `q w e r` (quantum names),
plus "any printable char" (insert/replace/search/command-line text).

**Browser artifacts** — three distinct kinds, and it matters that they are not the same problem:

1. **Spelling only, key is real.** `ArrowLeft`, `ArrowRight`, `ArrowUp`, `ArrowDown`, `Escape`, `Enter`,
   `Backspace` are DOM `event.key` names (TitleCase, and `Escape` not `Esc`). Every one of those *keys*
   exists on a terminal — so this is a naming/regression trap (`KeyCode::Esc` debugs as `Esc`, not
   `Escape`) rather than an unreachable token. **Consequence: no token in the current vocabulary has to be
   deleted.** The ticket's hypothesis ("anything relying on `event.key` names the browser invented") is
   true of the *names* and false of the *keys*.
2. **Silent-drop vocabulary the browser could emit and a terminal cannot.** Modifier-only keys
   (`Shift`, `Control`, `Alt`, `Meta`, `CapsLock`, `NumLock`, `ScrollLock`, `Fn`, `OS`), IME leftovers
   (`Dead`, `Process`, `Unidentified`, `Compose`) and `AltGraph`. These reach the server today as strings
   of length ≠ 1 and die in the handlers. A terminal sends **nothing** for bare modifiers, so the only work
   is to keep them out of the token stream — which the length rule already does.
3. **The real browser artifact: no modifier concept at all.** `event.key` collapses "which key" and "what
   it types", so the browser path *never* had to distinguish `Ctrl-d` from `d`. A terminal decoder does,
   and the byte aliasing (`Ctrl-i`=`Tab`, `Ctrl-m`=`Enter`, `Ctrl-h`=`Backspace`, `Ctrl-[`=`Esc`) is
   unrepresentable in the current vocabulary. This — not the TitleCase names — is the browser-shaped
   assumption that will bite (§6).

---

## 4. The lone-`ESC` ambiguity

**The problem.** `1b` alone is Escape; `1b` + more bytes is the start of every CSI/SS3/kitty sequence. A
decoder must decide whether to wait.

**How crossterm resolves it.** Crossterm's unix event source feeds stdin bytes into an internal parser
state machine that tracks "inside a possible escape sequence"; a lone `1b` is only emitted as
`KeyCode::Esc` once the parser knows no continuation is coming — either because the following byte is not
`[`/`O`/a valid continuation (which instead yields `Alt-<char>` from `1b`+char), or because a short
**internal timeout expires with the sequence still incomplete**. So the mechanism is *timeout*, and the
user-visible cost is that a bare Escape press is delivered after that timeout plus one event-loop turn.

> **Unverified in this run:** the numeric value of crossterm's ESC timeout. It is a private constant in
> crossterm's unix parser, this run had search only (no vendored source, no page fetch), and the constant
> does not appear in the public docs — `docs.rs` shows it only in the `src` view, which could not be
> retrieved. Do **not** bake a number into the spec from a blog post: read it from
> `crossterm-0.29.x/src/event/sys/unix/parse.rs`, or bracket it with §4.3 before the spec encodes a latency.

**4.1 The escape hatch that removes the timeout entirely.** The kitty keyboard protocol's *disambiguate
escape codes* flag makes `Esc` arrive as `CSI 27 u` — a complete, self-delimiting sequence needing no
lookahead (`CSI 27 u` = `1b 5b 32 37 75`). Crossterm exposes both the flag and the push/pop commands:
`PushKeyboardEnhancementFlags(KeyboardEnhancementFlags::DISAMBIGUATE_ESCAPE_CODES)` and
`PopKeyboardEnhancementFlags` ([docs.rs: PushKeyboardEnhancementFlags][pkef]). The same flag is what makes
`SUPER`/`HYPER`/`META` and keys like `ScrollLock` decodable at all ([docs.rs: KeyModifiers][km],
[KeyCode][kc]). Kitty's spec documents the push as `CSI > 1 u` at startup or on entering the alternate
screen ([kitty: comprehensive keyboard handling][kitty]).

*Researcher inference, stated as such:* with DISAMBIGUATE on, `Esc` is a fast unambiguous token and
`Alt-<char>` is told apart from `Esc`-then-`<char>` — exactly what a modal editor wants.
**Recommendation: push DISAMBIGUATE at attach and treat the timeout path as the degraded fallback**
(older terminals; tmux without extended keys). Either way the key adapter is unchanged: it still only has
to emit `"Escape"`.

**4.2 What it means for a user typing Escape.** In the fallback path, leaving insert mode costs one
timeout instead of one frame. Inside a 2 ms latency budget that would be the single largest input cost in
the system, and it is *heartbeat-visible* to a vim user. This is the strongest argument for (a) probing
kitty support at attach and (b) having the design's `≤ 0.1 ms` "key decode + adapter" budget
([components doc §Per-stage latency budget](../design/terminal-client-components.md)) explicitly exclude
the ESC-timeout fallback, since it cannot meet it.

**4.3 The 10-line check (do this before the spec fixes a number).** Under `enable_raw_mode`, run
crossterm's event loop, timestamp each `Event::Key` with `Instant::now()`, and measure the delta between
"bytes written to the pty" and "event delivered" for: a lone `Esc`; `Esc` then `[` 5 ms later; `Esc` then
`[` 40 ms later. That brackets the constant from outside without reading the source.

---

## 5. `KeyEventKind`: do `Release`/`Repeat` need suppressing?

- `KeyEvent` carries `kind: KeyEventKind` with `Press`/`Repeat`/`Release` and helpers
  `is_press()`/`is_repeat()`/`is_release()` ([docs.rs: KeyEvent][ke]).
- On **Windows** the console API reports press *and* release (and repeats), so every crossterm app must
  filter `kind == Press` or double-fire. Ratatui's own tutorial says so in as many words: "crossterm also
  emits key release and repeat events on Windows" ([ratatui: counter app][ratatui]).
- On **unix terminals** a plain terminal has no key-up concept: held keys auto-repeat *in the terminal or
  keyboard*, producing more press bytes and nothing else. Crossterm's own source comment frames
  `Repeat`/`Release` as the exception rather than the rule ([docs.rs: crossterm src event.rs][evsrc]). A
  `Release` can appear on unix only when the terminal speaks a protocol that reports it (kitty `CSI u`
  with event types).

**Recommendation:** the adapter keeps the filter unconditionally — `if kind != Press { skip }` — but does
**not** rely on it for de-duplication on unix, because auto-repeat arrives as repeated `Press` bytes. The
component doc already says "drops `Release`, collapses `Repeat` to one token per repeat"; the precise
reading must be *"forward one token per auto-repeat, i.e. do nothing special"* — collapsing a held key to a
single token would break held-key scrolling, which is the vim behaviour users expect. **Flag: the
components doc's wording is ambiguous here and the spec should restate it.** (Researcher inference, from
how terminal auto-repeat works.)

---

## 6. Modifiers: what decodes them, and does Visur's keymap have vocabulary for them?

**Decoding:** crossterm, and only crossterm — legacy terminals encode `Ctrl-<letter>` as the control byte
`0x01`–`0x1a` and `Alt-<char>` as `ESC`-prefix + char; crossterm turns those into `KeyCode::Char(c)` +
`KeyModifiers::CONTROL`/`ALT`. Richer combos (`Ctrl-Alt-Shift-x`, `Ctrl-;`) need CSI-u / the kitty
keyboard-enhancement flags above; without them those keys decode best-effort or not at all.

**Does Visur's keymap have modifier vocabulary today? No.** Direct evidence: every key in
`InitializerService` is a plain literal in a `HashMap<KeyPressed, VisurCommand>`, and `KeyPressed` has one
field, `String key` — no modifiers, no separate code/character. There is no `C-`/`M-`/`<C-x>` parsing
anywhere; `Keymap` cannot express a chord. So `Ctrl-d` and `d` are indistinguishable *by construction*, and
the only reason the browser "worked" is that `event.key` never emitted `Ctrl-d` as `"d"` with a modifier
attached — it emitted `"d"`, because the DOM drops modifiers into `event.ctrlKey`, **and the client never
sends that field** ([visur.js:132-134](../../../../src/main/resources/static/visur.js#L132-L134)). **This is
a live bug in the browser client, inherited for free by any naive terminal adapter.**

**Recommendation (this ticket's shape question): normalise, do not redesign.** Reasons:

1. The vocabulary needs no new *names* for any terminal key (§3.1) — a redesign buys nothing the spec needs.
2. Redesign touches `Keymap`, `KeymapMap`, `InitializerService`, `KeyPressed`, all nine handlers,
   `CommandStateService`, and the browser client — for a decision the language has not made (nobody has
   asked for `Ctrl-` bindings *in Visur's own command language*).
3. The honest option stays available later *without breaking the wire*: send
   `{"key": "<token>", "mods": [...]}` and treat a missing `mods` as `[]`. Old bindings keep working; the
   first `Ctrl-` binding is the moment `Keymap` needs a composite key.
4. **Do it now, cheaply:** have the client drop non-`NONE` modifiers (§2b) rather than forward them. That
   preserves the current behaviour's *intent* while making the omission explicit instead of accidental. The
   residual risk is silent unresponsiveness to `Ctrl-` keys — acceptable, and visible the first time
   someone tries one.

---

## 7. Bracketed paste

- Crossterm has it as a first-class feature: `EnableBracketedPaste`/`DisableBracketedPaste` commands
  (feature-gated `bracketed-paste`) and `Event::Paste(String)` — "only emitted if bracketed paste has been
  enabled" ([docs.rs: Event][event], [docs.rs: Command][cmd]). The terminal wraps pasted text in
  `ESC[200~` … `ESC[201~`; crossterm strips the envelope and hands over one `Paste` event.
- Without it, a paste is a burst of individual `Char` events, so a paste into insert mode becomes N separate
  `insertChar` commands (and any `\r`/`\t` in the clipboard fires `Enter`/`Tab`-shaped input) — a
  per-character round trip through the server under vim-parity latency targets.
- **Recommendation:** enable at attach, disable in the same `Drop` guard, and give paste its own client-side
  path because `Event::Paste` is *not* a `KeyEvent` — it cannot go through the key adapter. The components
  doc already assigns "paste → `PasteToken`" to component 2; the concrete correction is that its input is
  `Event::Paste(String)`, so that adapter is over `Event`, not `KeyEvent`. **Flag for the spec:** whether a
  paste becomes one `"<text>" insertChar`-shaped command or a dedicated paste message is ticket
  [08](../issues/08-decide-terminal-shape.md)'s payload call, and the protocol ticket's business if it adds
  a sibling message to `ViewUpdate`.

---

## 8. Raw mode on macOS, and `SIGWINCH`

**What `enable_raw_mode()` does.** On unix it `tcgetattr`s the tty, saves the termios, and `tcsetattr`s a raw
one: line discipline off (`ICANON` cleared → reads return per byte, no Enter required), echo off
(`ECHO`/`ECHONL` cleared → no double-printing, which matters because we do our own cursor addressing),
signals off (`ISIG` cleared → **Ctrl-C, Ctrl-Z, Ctrl-\ stop generating SIGINT/SIGTSTP/SIGQUIT** and arrive
as bytes `03`/`1a`/`1c`), flow control off (`IXON` cleared → Ctrl-S/Ctrl-Q pass through as bytes), and
`VMIN=1, VTIME=0` so a blocking read returns as soon as one byte exists. That flag set is the standard
`cfmakeraw` recipe ([termios(3)][termios]). **Unverified in this run:** that crossterm clears exactly this
set on macOS. The visible consequence (Ctrl-C is a byte, not a signal) is the well-known behaviour of every
crossterm TUI and is safe to design against, but confirm with a two-line test before the spec asserts it.

**Consequences the design must absorb (this is the part that changes tickets):**

- **Ctrl-C must be a client decision, not a signal.** In raw mode nothing kills the process; `03` arrives as
  `Char('c')` + `CONTROL`, which is not a Visur token, so if the client merely drops it the user has no way
  out of a wedged client. Recommendation: intercept `Ctrl-C` (and `Ctrl-Q`/`Ctrl-\`) in the adapter as
  client-local actions — "quit"/"detach" — and say so in the spec. A spec line of the form "restore on
  `SIGINT`" is therefore mostly **unreachable from the keyboard**; SIGINT can only arrive from outside
  (`kill -INT`).
- **Restore is not RAII, so it must be hand-built twice.** `?2026l` (if mid-frame) → `?25h` → disable
  bracketed paste → pop keyboard-enhancement flags → `?1049l` → `disable_raw_mode()`, run from (a) a `Drop`
  guard on the normal and error paths and (b) a `panic::set_hook` for the panic path. `Drop` does not run on
  `panic=abort`; the hook does not cover `SIGKILL`; document `reset`/`stty sane` as the recovery story.
  (Pattern is researcher inference from termios/crossterm's non-RAII API; no crossterm doc claims to do this
  for you.)
- **Buffering.** `write(2)` per frame means writing to fd 1 directly — not through `std::io::Stdout` (a
  `LineWriter`, so escape bytes with no `\n` sit in the buffer until flush) and not through crossterm's
  `queue!`/`execute!` machinery either (docs: "Commands must be executed/queued … otherwise they do
  nothing"; `queue!` buffers, `execute!` flushes — [docs.rs: Command][cmd]). One `write` of one pre-built
  `Vec<u8>` per frame satisfies the emitter's "one write(2) per frame" claim.
- **`?1049`: yes.** It saves the cursor and screen, clears, and restores scrollback on exit — exactly what a
  full-screen painter wants — and it sidesteps per-terminal differences between `?47`, `?1047` and `?1049`.
  The design already assigns it to the emitter (component 6) and to the forced-repaint triggers
  (component 5). Consequence to state: in the alternate screen the terminal's own scrollback is unavailable,
  so scrolling is Visur's job — which matches the server-side `canvasStart`/`fy` model rather than tmux's.
- **`?2026` synchronized output**: crossterm exposes `BeginSynchronizedUpdate`/`EndSynchronizedUpdate`
  (present in 0.27–0.29; added in 0.26.1 — [docs.rs: terminal][term], [ratatui discussion #579][sync]). We
  emit the bytes ourselves per the design, so this is only a fallback/confirmation; either way the bracket
  must be **probed**, because an unsupported `?2026` is silently ignored (harmless) while a supported one
  removes tearing entirely.
- **`SIGWINCH`**: crossterm installs the handler and surfaces `Event::Resize(cols, rows)`
  ([docs.rs: Event][event]); `terminal::size()` gives the current `WindowSize` via `TIOCGWINSZ`
  ([docs.rs: terminal][term]). Same path on macOS as on Linux. Two rules for the loop: (1) resize forces a
  full repaint and invalidates the shadow (the design says so already); (2) coalesce bursts, because a
  dragged window edge emits many `SIGWINCH`es and each costs a full repaint.

---

## 9. What you lose under tmux (or a wrapper)

| Loss | Consequence | Mitigation |
|---|---|---|
| **`?2026` may not pass through.** DECSET 2026 support is recent in tmux: it appears in tmux's `CHANGES` as "*Add support for applications to use synchronized output mode (DECSET 2026)*" ([tmux CHANGES][tmuxchanges]) — landing alongside OpenBSD 7.9 (May 2026), i.e. tmux **3.5**. Reports of tmux **3.4** supporting it require an explicit opt-in: `set -as terminal-features ',xterm*:sync'` ([claude-code #37283][cc2026]). | Tearing/flicker on repaint through tmux, corrected only after many rows. | Probe. If unavailable, either accept it or keep frames small (row-run damage helps). Do not version-check in place of a probe. |
| **The kitty keyboard push (`CSI > 1 u`) is swallowed.** tmux traditionally has no handler for the push, so `DISAMBIGUATE_ESCAPE_CODES` never takes effect *and the app never learns it failed* ([claude-code #29129][ccesc] — the "50 ms hardcoded Esc delay in tmux" symptom). tmux has its own path: `extended-keys` and `extended-keys-format [csi-u \| xterm]` ([tmux(1)][tmuxman]). | Lone `Esc` falls back to the timeout; `Ctrl-Alt-<key>` may arrive as legacy bytes; `Esc` vs `Alt-` stays ambiguous. | Treat DISAMBIGUATE as best-effort: push, verify by observing `CSI 27 u`, keep the timeout path correct, and read `$TMUX` to know you are multiplexed. |
| **Prefix key (`C-b` by default) never reaches the client.** | If Visur ever binds `Ctrl-b` it is unreachable under default tmux. Irrelevant today (no modifiers), relevant the day modifiers land. | Note it for the modifier discussion; don't bind `C-b`. |
| **Resize is pane-shaped and one hop late.** tmux delivers `SIGWINCH` to the pane's child when the *pane* resizes; if the outer window changes but the layout keeps the pane the same size, no resize happens at all. | Stale grid until the next pane resize. | Same handling; nothing extra to design. |
| **`TERM`/terminfo differences** (`screen-256color`, `tmux-256color`), and DA1/`XTGETTCAP` may be answered by tmux, not the outer terminal. | Capability probing (`2026`, truecolor) reports tmux's capabilities rather than the real terminal's — which is the *correct* answer, since tmux is what paints. | Probe `TERM`-aware; never assume the outer terminal's features reach you. |
| **Extra process hop + tmux's own repaint.** | Tens of microseconds to a few ms extra; tmux also re-diffs its own screen. | Budget note only; it fits inside the 1–2 ms target but should be measured once under tmux. |

---

## 10. Output: what part A resolves to

1. **Alternate screen: yes** (`?1049`), entered once at attach, left in the restore path.
2. **Synchronized update: yes** — `?2026h`/`?2026l` around each frame, **probed**; crossterm's
   `BeginSynchronizedUpdate` exists if raw bytes ever need company.
3. **Restore must be explicit**: `Drop` guard + panic hook, full sequence as in §8. Nothing in crossterm does
   this for you and `enable_raw_mode` is not RAII.
4. **Bypass buffering**: one `write` to fd 1 of a pre-built frame; never `println!`, never `stdout()`'s
   `LineWriter`, never `queue!` without `flush`.
5. **Ctrl-C is not a signal in raw mode** — the client owns exit (§8).

---

## 11. Concrete places the browser-shaped assumptions must change

A checklist for the spec's key section.

1. **[visur.js:132-134](../../../../src/main/resources/static/visur.js#L132-L134)** — `event.key` straight onto
   the wire is replaced by decoder + adapter. The wire *shape* (`{"key": "..."}`) survives; the *provenance*
   changes from DOM to bytes.
2. **The token vocabulary is `7 names + any single char`** — the spec must state this closed grammar, because
   `Keymap` silently ignores everything else. Today that silence is load-bearing.
3. **Modifiers become real.** The client must filter `KeyModifiers != NONE` (except `SHIFT`) instead of
   forwarding, or `Ctrl-d` deletes a quantum. A *new* responsibility with no browser precedent.
4. **`Tab`, `Ctrl-i`, `Ctrl-m`, `Ctrl-h`, `Ctrl-[` are byte-aliased** with `Tab`/`Enter`/`Backspace`/`Esc`.
   Decide each once, in the spec; a decoder cannot recover the difference.
5. **`Escape` gains a latency** (timeout) unless DISAMBIGUATE is on — the per-stage budget must say which path
   it is budgeting.
6. **Paste becomes its own event** (`Event::Paste`), not a burst of chars: the adapter's input type is
   `Event`, not `KeyEvent`, and the payload is ticket 08/03's call.
7. **`ArrowUp`/`ArrowDown` are in the navigate/insert/replace keymaps but not in `CommandStateService`'s
   branches** — Up/Down in the command line are currently no-ops; the terminal client will faithfully deliver
   them and they will do nothing. Not this ticket's fix; recorded so the spec does not "discover" it later.
8. **Nothing sets `isInCommandState = true`** (no keymap literal compiles it) — command state is unreachable
   by key today. The spec needs a deliberate binding.
9. **`Release`/`Repeat` wording in the components doc** needs restating: forward one token per auto-repeat,
   drop `Release` (§5).
10. **Raw mode makes `SIGINT` unreachable from the keyboard**: the restore story must be "restore on
    exit/panic/SIGTERM/SIGHUP", plus a client-owned Ctrl-C action.

---

## Contradictions

- **tmux 3.4 vs 3.5 for `?2026`.** A project issue states "tmux 3.4+ supports synchronized output passthrough —
  when configured with `set -as terminal-features ',xterm*:sync'`" ([claude-code #37283][cc2026]), while tmux's
  `CHANGES` records DECSET 2026 as a *new* feature in the release shipped with OpenBSD 7.9
  ([tmux CHANGES][tmuxchanges], [OpenBSD 7.9][obsd]) — i.e. tmux 3.5. **Reading:** 3.4 has opt-in passthrough,
  3.5 has native support. The disagreement does not change the recommendation (probe, don't version-check).
- **`KeyEventKind` on unix.** crossterm's source comment frames `Repeat`/`Release` as arriving from
  autorepeat/enhanced protocols, ratatui's docs say "on Windows" ([evsrc][evsrc], [ratatui][ratatui]). These
  agree in practice (Press is the norm on unix, Release is exotic) but neither is a spec sentence. It only
  matters if Visur ever binds a release event; it will not.

## Missing evidence

1. **crossterm's exact ESC timeout constant — not obtained.** Search-only tooling; the constant lives in
   `crossterm/src/event/sys/unix/parse.rs` and is not in the public docs. Mitigations: §4.3's measurement, or
   read the file before the spec quotes a number. **Do not cite a blog value.**
2. **crossterm's exact macOS raw-mode flag set — not read from source.** The `cfmakeraw` semantics are standard
   ([termios(3)][termios]); the Ctrl-C-can't-signal consequence is universally observable but unverified here
   from crossterm source.
3. **crossterm version pin** — docs point at **0.29.0** as current ([docs.rs: Event][event]); the
   `BeginSynchronizedUpdate` API dates to 0.26.1 ([sync][sync]). Which version the client pins is the build's
   call, outside this ticket.
4. **No byte-level capture from a real macOS terminal or tmux** was possible (no client exists yet). Every byte
   column in §2 comes from the standard escape repertoires, not from a capture. Cheap close: the prototype's
   key dumper, once ticket [08](../issues/08-decide-terminal-shape.md)'s spine exists.
5. **`source_check` was unavailable**, so no external claim above was machine-validated against fetched page
   content. Confidence labels reflect that.

## Sources

**Kept — Visur (primary, repository)**

- [KeyPressed.java](../../../../src/main/java/com/ple/visur/KeyPressed.java) — the token *is* a bare string; no validation, no modifiers.
- [Keymap.java](../../../../src/main/java/com/ple/visur/Keymap.java) / [KeymapMap.java](../../../../src/main/java/com/ple/visur/KeymapMap.java) — `HashMap<KeyPressed, VisurCommand>` + handler fall-through; why everything else is silently ignored.
- [KeyWasPressedVerticle.java](../../../../src/main/java/com/ple/visur/KeyWasPressedVerticle.java) — the `{"key": ...}` wire shape and the command-state/keymap split.
- [InitializerService.java](../../../../src/main/java/com/ple/visur/InitializerService.java) — all nine keymaps, the explicit literals, `KeyToQuantumName` = `q w e r`.
- The nine handlers + [CommandStateService.java](../../../../src/main/java/com/ple/visur/CommandStateService.java) — the `length() == 1` contract and the second key consumer.
- [visur.js:132-134](../../../../src/main/resources/static/visur.js#L132-L134) — `event.key` as the source of the vocabulary (and the discarded `ctrlKey`/`altKey`).

**Kept — external (API/spec/doc pages, seen via search snippets)**

- [docs.rs: `Event`][event] — `Event::Paste` "only emitted if bracketed paste has been enabled"; `Event::Resize(u16, u16)`.
- [docs.rs: `PushKeyboardEnhancementFlags`][pkef] — `DISAMBIGUATE_ESCAPE_CODES` and the push/pop pattern.
- [docs.rs: `KeyEvent`][ke] / [`KeyModifiers`][km] / [`KeyCode`][kc] — `kind`, `is_press`, which keys/mods require the enhancement flag.
- [docs.rs: `Command` trait][cmd] — `execute!` vs `queue!` vs flush.
- [docs.rs: `terminal`][term] — `BeginSynchronizedUpdate`/`EndSynchronizedUpdate`, `EnterAlternateScreen`, `WindowSize`.
- [docs.rs: crossterm src `event.rs`][evsrc] — the `Repeat`/`Release` comment.
- [kitty: comprehensive keyboard handling][kitty] — `CSI 27 u` for Escape under disambiguate, `CSI >1u` push, modifier/repeat encoding.
- [termios(3)][termios] — the raw-mode flag set and `cfmakeraw`.
- [tmux CHANGES][tmuxchanges], [tmux(1)][tmuxman], [OpenBSD 7.9][obsd] — DECSET 2026 support; `extended-keys` / `extended-keys-format csi-u`.
- [ratatui counter-app tutorial][ratatui] — corroboration that `KeyEventKind` matters on Windows (not treated as authority).
- [claude-code #37283][cc2026], [#29129][ccesc] — field reports of tmux's `?2026` and kitty-push gaps; treated as leads corroborated by tmux's own `CHANGES`.

**Rejected / deprioritized**

- Blog posts on terminal key encoding and TUI internals — secondary restatements of the kitty spec, which was available directly.
- `r3bl_tui` / `edtui` / `fresh` docs and changelogs — evidence only that others hit "ESC disambiguation lag" and bracketed-paste leaks; no authoritative numbers.
- Reddit/StackOverflow answers on `termios` raw mode — superseded by `termios(3)`.
- Generic search hits returning unrelated PDFs/text corpora — the search backend degraded on code-shaped queries; noted so this brief's gaps are not mistaken for absence of evidence.

## Next steps

1. **Read `crossterm-0.29.x/src/event/sys/unix/parse.rs`** (vendored, or the `docs.rs` `src` view) for the ESC
   timeout constant, and crossterm's unix `terminal` backend for the raw-mode flag set — the two remaining
   unverified numbers, neither of which the spec should guess.
2. When the prototype spine exists, **capture real bytes** on macOS through Terminal.app/iTerm2 and through
   tmux for: `Esc`, `Alt-x`, `Ctrl-x`, arrows with and without DECCKM, a paste, and a resize — then correct §2
   if the capture disagrees.
3. Hand §2 + §11 to the spec's key section; hand §9 (probe, don't version-check) and the `?2026`/kitty probing
   to [08](../issues/08-decide-terminal-shape.md).

[event]: https://docs.rs/crossterm/latest/crossterm/event/enum.Event.html
[pkef]: https://docs.rs/crossterm/latest/crossterm/event/struct.PushKeyboardEnhancementFlags.html
[ke]: https://docs.rs/crossterm/latest/crossterm/event/struct.KeyEvent.html
[km]: https://docs.rs/crossterm/latest/crossterm/event/struct.KeyModifiers.html
[kc]: https://docs.rs/crossterm/latest/crossterm/event/enum.KeyCode.html
[cmd]: https://docs.rs/crossterm/latest/crossterm/trait.Command.html
[term]: https://docs.rs/crossterm/latest/crossterm/terminal/index.html
[evsrc]: https://docs.rs/crossterm/latest/src/crossterm/event.rs.html
[kitty]: https://sw.kovidgoyal.net/kitty/keyboard-protocol/
[termios]: https://man7.org/linux/man-pages/man3/termios.3.html
[tmuxchanges]: https://github.com/tmux/tmux/blob/master/CHANGES
[tmuxman]: https://man.openbsd.org/tmux
[obsd]: https://www.openbsd.org/79.html
[ratatui]: https://ratatui.rs/tutorials/counter-app/basic-app/
[cc2026]: https://github.com/anthropics/claude-code/issues/37283
[ccesc]: https://github.com/anthropics/claude-code/issues/29129
[sync]: https://github.com/ratatui/ratatui/discussions/579
