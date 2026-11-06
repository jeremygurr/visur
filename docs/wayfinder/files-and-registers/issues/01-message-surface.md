# Errors reach the screen

Type: task
Status: open
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

**Two lifetimes, one surface.** Command errors — `loadFile: file not found: x` — clear on the next keypress. **A failed
save does not clear until the user acts**, because "your save failed and you now have unsaved work" must not scroll away
under more typing. This is the one place where a message has to be sticky, and it is the difference between an
inconvenience and losing a document.

**`reportError` keeps its name and one job:** write the message to the model, and keep printing to stdout so the log is
still the log. Nothing else calls `System.out` to report a problem.

**Both clients draw it, and both get a test.** The terminal renders it in `screen.rs`, the browser in `visur.js`; the
existing mode-line clip test is the model for asserting it survives 40 columns, because an error long enough to be
useful is long enough to clip.

## Done when

- `reportError` writes to the model; the message reaches the view payload and both clients draw it.
- A failed `:open` of a missing file shows the reason **on screen**, asserted through the view payload, not through
  stdout.
- A command error clears on the next keypress; a failed save does **not**, and the test says which is which.
- The message survives the 40-column clip, or is clipped in a way the test states out loud.
- The three `FileIoTest` tests that claim to assert error reporting assert it for the first time:
  `saveFileWithoutPathOrSourceReportsError`, `loadFileMissingFileLeavesDocumentUntouched`,
  `loadFileNoArgumentReportsError`. They currently assert only that the document is unchanged and would pass with
  `reportError` deleted entirely.
- [05](05-command-key-and-grammar.md)'s `Error writing: No filename provided` is rendered by this surface and nothing
  else.

## Notes

- The field is one string, not a severity, not a code, not a list. A second message at a time is a different design and
  nobody has asked for it.
- `View.render()` is a fixed field list (`:119-141`) that `terminal/src/protocol.rs:59-93` decodes key by key. Adding a
  field means both sides in the same commit; the coding standard is no compatibility windows.
- Do **not** put the message in `commandStateContent` and reuse that field. Command state is user input with its own
  cursor and history, and overloading it means the message is editable and gets recalled by `ArrowUp`.
- The three-way swap prompt in [06](06-swap-files.md) needs *three* answers, which is a question rather than a message.
  This ticket deliberately ships the message half; the prompt is that ticket's, and the reason it can wait is that a
  message is the thing standing between the user and lost work.
- `docs/reference/keys.md` gains nothing here; `CODING_STANDARDS.md` gains a line that the model is where a message lives,
  because "report an error" now has a destination that a test can read.
