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

- [x] The server can ask a client to write its clipboard and can receive a read reply, over both `/ws` and the SockJS bridge.
  Each direction has a test at the wire seam, and the Rust test that **drops unknown addresses** is updated to say which
  addresses it understands and why the rest are still dropped.
- [x] A test asserts the OSC 52 bytes the terminal client emits, including base64 and the buffer selector, so an escaping bug
  cannot ship as a silent no-op — which is exactly how `\` shipped.
- [x] The write is coalesced per command, asserted where three writes would otherwise be visible.
- [x] A read failure produces the message and **no paste**. Asserted explicitly: the document is unchanged and the message
  names the clipboard.
- [x] The cache is correct: after a yank, `p` from `|0` works with the client offering no read at all.
- [x] The transport that carries a clipboard request refuses a non-loopback peer, asserted the way `/ws`'s check is
  asserted. (Closed before this ticket opened: ticket
  [02](02-viewport-per-client.md) left `/ws` as the only way in.)
- [x] The documented reality is in `docs/reference/keys.md` and the README: **copy out works over SSH on mainstream
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

## What landed

**Three addresses, not two.** `clipboardWriteRequested` and `clipboardContents` were the sketch; a read needs a question
of its own, so `clipboardReadRequested` joins them. The inbound answer is one address carrying two facts: `text` present
is an answer — `""` included, because a clipboard that holds nothing *answered* — and `text` absent is the client saying
it could not read, which is the only thing it can honestly say when Terminal.app has no OSC 52 read, iTerm2 gates the
query and tmux swallows it. Present-or-absent rather than a flag is what keeps an empty clipboard and an unreadable one
from collapsing into each other, and they must not: one is a fact about the user's machine, the other is the reason not to
trust the machine's memory.

**Buffer `c`, not `0`.** The sketch said `;0;`. The `0`–`7` buffers are DEC cut buffers, which nothing that also has a
CLIPBOARD still means; `c` is what xterm maps to CLIPBOARD and what iTerm2 documents. The bytes are asserted exactly, in
Rust and again through a pty, because an OSC 52 sequence that is one character wrong is not a failure — it is a clipboard
write that never happened, which is how `\` shipped once already.

**The pending slot is the correlation id.** One outstanding question per window, held on that window's session as an
`AtomicReference<Long>` of its timer id, and the answer and the patience timer race for it with `getAndSet`/`compareAndSet`
because they run on the event loop without reference to one another. Whoever takes the slot wins; the other does nothing.
A reply that arrives after its own patience ran out is a reply to nothing, and dropping it is the whole of the stale-paste
defence. Two of this design's guards passed their first mutation attempt and had to be mutated again properly — a
mutation that throws instead of honouring a reply also "passes" — which is worth writing down because both were
convincing, and neither was the code being right.

**A failed read takes the cache with it**, which is what the ticket's "no paste" turns into once it is implemented, and
needed a decision the ticket did not make: on refusal or silence, `Clipboard.cache(null)` runs. The server just learned it
cannot check, so what it remembers is a guess, and 09's fast path — paste the memory, ask nobody — would paste that guess
quietly. So the memory is destroyed at the moment it stops being trustworthy. The cost is nothing real: a fresh yank
repopulates it, and a clipboard visur has never written was never knowable in the first place.

**Half a second of patience** on the server, 120 ms and 300 ms on the client for the permission query and the contents.
The numbers are in each other's comments on purpose: the client has to fail before the server gives up, so what the user
reads is the client's honest "this terminal will not say" rather than the server's guess about a slow one.

**Base64 by hand**, in the Rust client, because the alternative was a dependency for thirty lines in a program whose whole
job is bytes. It is checked against RFC 4648's vectors, against every character of the alphabet, against malformed
padding, and — the check that matters for a clipboard — against the system `base64`'s output for a 79-byte UTF-8
paragraph, which is not something the file was written against.

**What "one write per command" turned out to be.** The map's example is `3 d`, and `3 d` was never going to issue three
writes: at the delete operator the count multiplies the *extent*, not the number of operator calls, so it yanks once. The
commands that could issue several are the ones that do a thing several times — `3 p`, a repeat of a multi-edit session —
and `p` already coalesces in the model, because the register records what a paste displaced once however many copies it
inserted. So the coalescing is structural rather than needed: an operator *offers* text to its window, and one frame goes
out when the command ends, carrying what the command settled on. `3 p` is pinned as one frame; the offer slot is pinned as
"last wins, and spent whether or not it was sent", the spending being the half that matters or the next command sends the
last one's text to whichever window typed it.

**A command with no window caches nothing.** A headless yank writes nobody's clipboard, and caching it would be the server
claiming to know what a clipboard holds because it thought about writing one. That test is the one this ticket was built
backwards from: it fails under the mutation that checks for a window before taking the offer, and its `assertNull` is the
falsifiable form of "the cache is what the server actually sent".

**What is not asserted, and why.** "A read failure produces no paste" is asserted as the mechanism that makes it true —
the message, and the cache taken away — because the key that pastes from the clipboard is `|0 p`, and `|` belongs to
[09](09-register-model.md). Likewise "after a yank, `p` from `|0` works with the client offering no read" is pinned as the
yank landing in the memory with nothing asked of the client: the whole fast path minus the key that takes it. 09 inherits
`ClipboardContents.ask(vertx, connection)`, and will want a caller with a `Vertx` in hand; the shape it wants is the
flush's — mark the need during the command, ask when the command ends — recorded there rather than invented here.

**The client is a dispatcher now.** `protocol::inbound` answers what a frame *is* — a view, a clipboard to write, a
clipboard to read, or nothing — and the test that used to assert "frames that are not views are dropped" now names the
three addresses the client speaks and why the rest stay dropped. `clipboardWriteRequested` is in that list rather than in
a `match` in the loop for the ticket's own reason: dropping a request is how a feature silently does nothing.

**Four pty tests make the fake server the terminal.** One test answers the permission query with `n` and asserts the
client does not then ask for the contents; another sends the clipboard back and asserts the server is told, decoded rather
than as base64; a third says nothing and asserts the client reports the silence. The last mile nobody can check here is a
real emulator's behaviour — iTerm2's dialog, Terminal.app's blank stare, tmux's swallowed query — and `keys.md` says so
out loud rather than implying a check that was not run.

**One refactor bug, caught by the suite that exists for it.** Sharing one raw-read loop between the capability probe and
the clipboard read deleted the probe's *write* along with its loop: the client probed nothing, and the two pty tests that
watch the probe failed loudly. That is the argument for having a pty suite at all, and for why the clipboard's bytes are
asserted twice, once in a unit test and once leaving a process.
