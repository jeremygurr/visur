# Clipboard transport: the server asks, the client answers

Type: task
Status: open
Blocked by: 01

## Work

**The server never touches an OS clipboard. It asks the client**, because over SSH the client is the half on the user's
machine, and because visur is going to be its own terminal emulator — so the protocol is designed for an emulator we
control, and today's third-party terminal is a partial implementation of it. That is a premise of this map, not an
optimisation.

**Nothing server→client exists today.** `viewWasChanged` is the only outbound address (`MainVerticle.java:58`), it carries
no command or request field (`View.render():119-141`), and the Rust client **drops any frame whose address is not it**
(`protocol.rs:60-62`, asserted by a test). So this ticket builds a channel, and the clipboard is its first user.

**Two addresses.** `clipboardWriteRequested {text}` outbound, `clipboardContents {text}` inbound, plus the bridge's
outbound allow-list widened to include the first (`MainVerticle.java:54-58`) and a dispatcher in the Rust client that
stops dropping what it does not recognise — dropping an unknown *reply* is fine; dropping a *request* is how this feature
silently does nothing. **Do not put clipboard operations in the view**: the view is a snapshot re-rendered on every model
change, and an event in it either replays on redraw or needs a sequence number, which is this ticket rebuilt worse.

**Write, in the terminal:** one `write_all` of the OSC 52 sequence to the stdout the client already owns raw
(`main.rs:41-46` — unbuffered fd 1, alternate screen, `Restore` guard). Base64, `;0;` buffer selection, and the `M`
substitution/paste-control query are the parts that decide whether it works on a given emulator; they get a test that
asserts the exact bytes rather than one that asserts it "sent something".

**Read, in the terminal, is the hard half and it is built anyway.** The user chose `|0` + `p` pasting an externally
copied clipboard over "use the terminal's own paste key", so the read path is in the protocol from the start even though
the current terminal client is a best-effort implementation of it. Two facts make it hard, and both belong in the ticket
rather than in a bug report later: OSC 52 reads are not implemented by Terminal.app, are gated behind a preference in
iTerm2, and are swallowed by tmux; and **the reply cannot be read through the event loop at all** — the only raw stdin
reader in the client is `probe_sync_output` (`main.rs:71-94`), run once before the loop, and `crossterm::event::read()`
would consume a reply as noise. So the read brackets its own raw read around the query, or it does not happen.

**The cache makes the read rare.** The server remembers the last thing it wrote to the clipboard, so `p` from `|0` needs
no read at all for anything copied inside visur — which is the overwhelming majority of it. The read is for text copied
outside, and when it is unavailable that is [01](01-message-surface.md)'s message, and **the paste does not happen**. A
quiet `p` that pastes a stale clipboard is the one outcome worse than an error.

**One write per command.** A yank, a delete, and `p`'s displaced text are one command; `3 d` is one write. Terminals beep
at OSC 52 spam and every write replaces the user's real clipboard, so the coalescing is a feature, and the test asserts
one write where three were issued.

**The loopback question is already closed.** This ticket used to require `/eventbus` get the check `/ws` had, because a
remote browser could drive a local editor and a page that can push text into a clipboard makes that a real problem.
Ticket [02](02-viewport-per-client.md) deleted the bridge: the browser speaks `/ws` now, so there is one way in and it
already refuses non-loopback peers. SSH stays the way to be remote, and what is left here is the **policy** question —
whether loopback is the right boundary once a socket can write the user's clipboard — not a missing check.

## Done when

- The server can ask a client to write its clipboard and can receive a read reply, over both `/ws` and the SockJS bridge.
  Each direction has a test at the wire seam, and the Rust test that **drops unknown addresses** is updated to say which
  addresses it understands and why the rest are still dropped.
- A test asserts the OSC 52 bytes the terminal client emits, including base64 and the buffer selector, so an escaping bug
  cannot ship as a silent no-op — which is exactly how `\` shipped.
- The write is coalesced per command, asserted where three writes would otherwise be visible.
- A read failure produces the message and **no paste**. Asserted explicitly: the document is unchanged and the message
  names the clipboard.
- The cache is correct: after a yank, `p` from `|0` works with the client offering no read at all.
- The transport that carries a clipboard request refuses a non-loopback peer, asserted the way `/ws`'s check is
  asserted. (Closed before this ticket opened: ticket
  [02](02-viewport-per-client.md) left `/ws` as the only way in.)
- The documented reality is in `docs/reference/keys.md` and the README: **copy out works over SSH on mainstream
  emulators; pulling text copied outside visur works in visur's own emulator and in the browser, and not on a third-party
  terminal that has no OSC 52 read.** Naming the limit is what stops it being filed as a regression.

## Notes

- OSC 52 payloads are capped by emulators (commonly around 74999 bytes) and a too-long write is truncated or dropped.
  Decide in the ticket whether a too-large yank copies nothing, copies a prefix, or refuses with a message. Silent
  truncation of a clipboard is data loss.
- The clipboard holds text only, and `\n` is the document's own newline: what the selection covers is what goes out,
  separator toggle included (that decision lives with [09](09-register-model.md), which owns what "the selection" is).
- The Rust client's deliberate rule — drop every CONTROL/ALT/SUPER chord — is untouched. Clipboard keys are ordinary
  characters.
- If the SockJS bridge turns out to need a per-message type registration rather than an address pattern, that is a
  protocol fact worth recording here, because the browser clipboard in
  [10](10-browser-clipboard.md) depends on the same two addresses.
- Resist the helper binary (`pbcopy`/`wl-copy`/`xsel`) as a shortcut. It would make the current terminal client fully
  bidirectional and would be the third thing to keep working, in a design whose whole premise is that the client is ours
  soon. If the read truly cannot be made to work, that is a decision to make in writing, not a binary to slip in.
