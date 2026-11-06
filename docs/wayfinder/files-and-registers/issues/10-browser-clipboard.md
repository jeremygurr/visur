# Browser clipboard both ways

Type: task
Status: open
Blocked by: 08

## Work

**The browser page cannot paste today.** `visur.js` has exactly one input listener, `keydown` (`:65-67`), and sends
`event.key` for it. `pasteWasReceived` exists, is bridged inbound, and the server handles it (`handlePaste` →
`pasteText`, inserting at the caret as one undo unit) — but only crossterm's `Event::Paste` has ever sent one. Cmd-V in
the browser page inserts nothing. Meanwhile each `Control`/`Meta` key press arrives as a keypress that matches no binding,
so a paste attempt sends `Control` and `v` as two no-ops.

**Paste in: a `paste` event, one message.** The browser puts the clipboard text on the event, so no permission prompt and
no `navigator.clipboard` call is involved at all. Send the whole block as `pasteWasReceived`, which the server already
treats as one insertion and [ticket 06 of the last map](../../separators-and-undo/issues/06-undo-redo.md) made one undo
unit. This is the cheapest user-visible win in the whole map: the feature exists server-side and has been waiting for a
client that asks.

Two things to decide in the open rather than discover: a `paste` event **inside command state** should insert its text
into the command line (`:open ` + a path from an explorer is exactly this case) or be refused, and a paste whose text
contains `\n` must not be turned into a series of keypresses — that is the difference between a paste and typing, and the
server distinguishes them by address, so the browser must send the right one.

**Copy out: `clipboardWriteRequested` → `navigator.clipboard.writeText`.** [08](08-clipboard-transport.md)'s outbound
address, handled by the browser the way OSC 52 is handled by the terminal. `writeText` needs a transient user activation
and `clipboard-write` permission, which Chrome grants to a secure context without a prompt — and localhost is a secure
context, which is what makes this work at all. **If someone ever serves visur over a plain http URL on a hostname, this
breaks**, and that is worth a line in the ticket's `## Result` rather than a runtime surprise.

**Read: `clipboardContents` → `navigator.clipboard.readText()`,** which does prompt, once per origin, and is the only
route to an externally copied clipboard over SSH from the browser. When the user declines, that is the same
"clipboard unavailable" message as every other transport failure, and `p` still does not paste anything.

## Done when

- Pasting into the browser page inserts the clipboard at the caret as **one** undo unit, asserted at the wire seam: the
  browser sends one `pasteWasReceived` with the whole block, not N keypresses.
- A pasted block containing newlines, tabs and quotes arrives byte-identical — the JSON layer escapes it and the server
  un-escapes it once, which is the same class of bug that made `\` dead.
- Pasting in insert mode, navigate mode and command state each behave as decided, with the decision written down.
- Cmd-C (or `y` with `|0` selected) puts the selection in the real clipboard, and a line yank at the line quantum with
  `SEP=ON` includes the trailing newline.
- `p` with `|0` after an external copy prompts once, works, and pastes nothing if the user declines — with the message
  shown.
- The browser's existing `preventDefault` absence is kept where it should be and added where it must be: `Ctrl-r` must
  still reload the page (a documented consequence from the last map), while `Cmd-V`/`Ctrl-V`/`Cmd-C` must not be eaten by
  the page or turned into keypresses.
- The `keydown` handler ignores `Control`, `Meta`, `Alt`, `Shift` and `Dead` as keys, since they are modifiers arriving as
  presses today. That is not the clipboard's bug, but it is in the file the clipboard has to change and it is one line.

## Notes

- **`frontEnd/editingScreen.js` also exists** and is not the page the server serves
  (`src/main/resources/static/visur.js` is). Before editing, check which one the route actually serves
  (`MainVerticle.java:120-153`) — the repo has two front ends and the wrong one is a plausible-looking file.
- The browser page has **no test harness**, which the last map already said and which is why every criterion above is
  phrased as a message on the wire. The tests live on the server side of the boundary: given this `pasteWasReceived`, the
  document and undo ring behave thus. What cannot be tested here is that the browser sends it, so the manual check has to
  say so specifically.
- `navigator.clipboard.readText()` returns rich `ClipboardItem`s; take the `text/plain` representation and ignore the rest.
  Non-text payloads are out of scope for the whole map.
- Nothing in this ticket is visur-specific protocol design. If the same three handlers were written for a second client,
  they would look exactly like this, which is the point of [08](08-clipboard-transport.md) being a transport ticket and
  this one being an implementation of it.
- Manual check, and it is the one that matters: copy from the editor into another app, copy from another app into the
  editor, over an SSH tunnel from a remote server, in Chrome, with the browser page served from localhost.
