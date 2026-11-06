# Errors reach the screen

Type: task
Status: closed
Blocked by: —

## Work

`EditorModelCoupler.reportError` is `System.out.println(message)` (`:440-442`). Every error visur has ever produced —
load of a missing file, `saveFile` with no path, an unreadable file, a permission denial — goes to the server's stdout,
which the launcher redirects to `${TMPDIR:-/tmp}/visur-server.log`. On screen, a failed load is indistinguishable from a
key that does nothing. Every other ticket in this map that has something to tell the user depends on this one.

**A message slot in the model, rendered by both clients.** The view already carries `commandStateContent`
(`View.render()`), so the surface exists in the wire contract and in both renderers: add one field, drawn where the
command line draws. Not a new mode, not a popup, not a submode — the user is not in a different state when a file fails
to open.

**One lifetime: the next thing the user does.** Any keypress or paste clears the message, including after a failed save.
A message describes the last thing the user asked for; once they ask for something else it is stale, and a stale error on
screen is a false alarm about work that may well have saved fine the second time. Sticky was considered and rejected.

**`reportError` keeps its name and one job:** write the message to the model, and keep printing to stdout so the log is
still the log. Nothing else calls `System.out` to report a problem.

**Both clients draw it, and both get a test.** The terminal renders it in `screen.rs`, the browser in `visur.js`; the
existing mode-line clip test is the model for asserting it survives 40 columns, because an error long enough to be
useful is long enough to clip.

## Done when

- `reportError` writes to the model; the message reaches the view payload and both clients draw it.
- A failed `:open` of a missing file shows the reason **on screen**, asserted through the view payload, not through
  stdout.
- **Any keypress clears it, and so does a paste** — after a failed save as much as after a failed load. One lifetime for
  every message, asserted for both.
- The message survives the 40-column clip, or is clipped in a way the test states out loud.
- The three `FileIoTest` tests that claim to assert error reporting assert it for the first time:
  `saveFileWithoutPathOrSourceReportsError`, `loadFileMissingFileLeavesDocumentUntouched`,
  `loadFileNoArgumentReportsError`. They currently assert only that the document is unchanged and would pass with
  `reportError` deleted entirely.
- [05](05-command-key-and-grammar.md)'s `Error writing: No filename provided` is rendered by this surface and nothing
  else.

## Result

One string in the model under `EditorModelKey.message`. `reportError` writes it and still prints it; `getMessage` answers
empty when there is none, so the field is always on the wire; `View.render()` carries it; `screen.rs` and `visur.js` draw
it. That is the whole feature — 18 lines of production Java, four of JavaScript, one `let` in Rust.

**One lifetime, not two.** The Work section asked for a failed save to survive until the user acts, and that was overridden
while the ticket was open: any keypress or paste retires any message. "Until the user acts" was never a rule anyone could
implement without deciding that typing is acting, and a stale complaint about a save that worked the second time is worse
than no complaint. So there is no `persists` flag, no second entry point, and `SaveFileOp` needed no change at all. The
clearing sits at the **top** of `press` and `pasteText`, which is the half that matters and is why the test drives
keypresses rather than the model: clearing after the command runs would erase the message that command just reported,
inside the same keystroke.

**The message takes the terminal's tail.** `mode_line` puts `! message` where the quantum, `SCOPE=`, `SEP=` and `SPAN=`
were, so the mode word survives a forty-column window and the message is what clips at the right. `! ` is the only new
glyph; there is no room on that line for a word like `ERROR`, and the mode line has never used `!` for anything.

**Finding: no command had ever been typed successfully.** `press` opened command state and then fell through, handing the
same `;` to `executeCommandState`, which appended it to the line it had just emptied. So a command typed from the keyboard
was `;"path" loadFile`, which compiles to **zero operations** — the `;` is not a word any matcher knows, `RecallWord`
swallows it, and everything after an unparseable token is discarded. Both clients were drawing the prompt twice as well:
`command_line` in `screen.rs` prepends `;` and the content started with one, so the terminal showed `;;`. This is why the
ticket could not be finished at the keypress seam without fixing it: nothing typed could ever reach `reportError`. Fixed by
letting `enterCommandStateIfRequested` report that it entered, and `press` spend the key there.
`CommandStateServiceTest.semicolonEntersOnlyFromNavigateSubmode` already asserted the intended contract — content empty
after entering — by calling `enterCommandStateIfRequested` and `executeCommandState` separately, which is exactly the hole
this map's Notes warned about: the key that opens command state had never been pressed in a test. Ticket
[05](05-command-key-and-grammar.md) asks for that test; it exists now, as
`theKeyThatOpensCommandStateIsNotPartOfTheCommand`.

**The three named tests now assert an error, and the mutation check says so.** With `reportError`'s model write deleted,
five tests go red: the payload test, the lifetime test, and the three `FileIoTest` cases this ticket named — the first time
those three have been able to disagree with the code. The load-missing-file test asserts the message *names the path*, not
that it matches a sentence the operator could rewrite; wording is ticket 05's to change.

**Numbers:** Java 212 tests (from 208: one payload, two lifetime, one command-state), Rust 49 + 10 (from 48 + 10).

**Not checked: either client, by a human.** Everything above is asserted through the payload and the renderers' own unit
tests. The check that has not been run: start visur, type `;` `"/tmp/nope.txt"` `"` ` ` `loadFile` `Enter` — the terminal's
last row should read ` NAVIGATE MODE ! loadFile: file not found: /tmp/nope.txt`, with **one** `;` while typing the command,
and one more keypress should take the complaint away. In the browser the same message appears after `SPAN=`.

## Notes

- The field is one string, not a severity, not a code, not a list, and not a flag for how long it lives. A second message
  at a time is a different design and nobody has asked for it.
- Clearing happens at the **top** of the keypress path, before the command runs, so the message a command reports is on
  screen until the key after it — an error that cleared itself in the same keystroke that raised it would be invisible.
- `View.render()` is a fixed field list (`:119-141`) that `terminal/src/protocol.rs:59-93` decodes key by key. Adding a
  field means both sides in the same commit; the coding standard is no compatibility windows.
- Do **not** put the message in `commandStateContent` and reuse that field. Command state is user input with its own
  cursor and history, and overloading it means the message is editable and gets recalled by `ArrowUp`.
- The three-way swap prompt in [06](06-swap-files.md) needs *three* answers, which is a question rather than a message.
  This ticket deliberately ships the message half; the prompt is that ticket's, and the reason it can wait is that a
  message is the thing standing between the user and lost work.
- `docs/reference/keys.md` gains nothing here; `CODING_STANDARDS.md` gains a line that the model is where a message lives,
  because "report an error" now has a destination that a test can read.
