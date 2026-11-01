# Terminal client skeleton: connect, paint rows, take keys

Type: task
Status: open
Blocked by: 04

> [!note] The spike already speaks the new protocol
> [Ticket 03](03-switch-payload.md) moved it to `rows`, so it is a working reference for this ticket rather than
> something to repair first. What it does *not* have is any of this ticket's real work: damage tracking, the
> ordered terminal restore, `SIGWINCH`, or a launcher.

## Work

Spec §5.2–5.4, built to the component boundaries in [the design](../../renderer-delivery/design/terminal-client-components.md). Start from [the spike](../../renderer-delivery/spike/visur-term/src/main.rs) — it already solves the hard, uninteresting parts (raw mode, layout, cursor, key adapter) and predates `rows`, so its job is to be read and then mostly deleted.

Minimum: connect to `/ws`, decode the payload, paint `rows` (full repaint, no diffing yet), draw a mode line, and send key tokens for `h j k l`, arrows, `i`, `a`, `o`, `Esc`, printable characters, `Backspace`, `Enter`, `s`+digit, and search. Raw mode, alternate screen, and *no* SockJS.

Follow-the-cursor scrolling only: the renderer holds its viewport's rows and cannot scroll past them (spec §5.8).

## Done when

- Every key in the spec's **must** list except the terminal-lifecycle ones works against the real server, verified by driving it headlessly in a pty (the spike's `drive.py` does this).
- Typing feels immediate to a human; any perceptible lag is recorded with a number rather than a shrug.
- The client contains no wrapping arithmetic and no SockJS framing.
