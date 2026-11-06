# Map: files-and-registers

## Destination

**Visur edits a file you named, tells you on screen when the screen and the file disagree, and moves text between
visur and the rest of the computer.**

Concretely: `visur ~/notes/ideas.txt` opens that file. `:open other.txt` replaces the document with it, and refuses if
that would throw away text that exists only on your screen — which visur can tell, because it compares what is on
screen against what is saved and shows `*` when they differ. `:w` writes it, `:w newname.txt` names or moves it, and a
document that has never had a name answers `:w` with `Error writing: No filename provided` rather than silently
succeeding. `:q` refuses when there is unsaved text, `:q!` throws it away. None of this works by printing to a log file
you are not reading: an error appears on the screen.

Text moves out and back. `|` selects a register — `|1` through `|9` for the rotation of recent yanks, deletes and
paste-displacements, `|a`–`|z` for named slots nothing rotates, `|0` for the system clipboard — and `y`, `d`, `p` behave
identically whichever one is selected, so there is one set of keys to learn. With `|0` selected, a yank puts the text in
your real clipboard and `p` takes the clipboard back, including text copied in another application.

Underneath all of it: **the editor model stops conflating a document with a window.** `Document` owns content, path, the
saved baseline that makes dirty tracking possible, undo history and swap identity; `Session` owns cursor, quantum, span,
scope, submode stack, pending count, viewport and the selected register. That split is what makes the second client
attached to the server a feature rather than a bug — today its window resize rewraps the first one's screen.

This closes the [separators-and-undo](../separators-and-undo/map.md) fog entries on **named registers** ("still clobbered
unconditionally; now with a second way to clobber") and **undo across documents** ("the ring is per model… neither is
designed here"), and it is the first step towards the in-app screens, tabs and multiple documents this project is heading
for.

## Notes

Facts established before a ticket was written, each of which changes how a ticket has to be approached.

- **Save and load already work, and nothing on the keyboard reaches them.** `LoadFileOp` pops a path pushed by a quoted
  literal (`;"path" loadFile`), `SaveFileOp` falls back to `emc.getSourceFilePath()`, `ZZ` is bound to `saveFile`, and
  `VISUR_FILE` opens a file at boot. Verified end to end through `CommandCompileService` during grilling: the quoted form
  loads and sets the source path; `;loadFile path` **silently does nothing**, because `LiteralStringWord`'s regex
  `("[^"]*"*)(.*)` only matches a leading quote, so the bare path never compiles and `NativeOpWord`-then-nothing runs
  argument-less.
- **Save is already atomic.** `SaveFileOp` writes a temp file and renames it over the target, deleting the swap after a
  successful rename (`SaveFileOp.java:61`). Trailing-newline absence and CRLF are preserved byte for byte, and parent
  directories are created. Sixteen tests in `FileIoTest` hold this. **No ticket re-does it.**
- **Every file error goes to a log file.** `EditorModelCoupler.reportError` is `System.out.println` (`:440-442`), and
  the launcher sends server output to `${TMPDIR:-/tmp}/visur-server.log`. A load of a missing file and a key that does
  nothing are indistinguishable on screen. This is why [01](issues/01-message-surface.md) is first.
- **Four existing file tests pass whether or not the feature works.** `saveFileWithoutPathOrSourceReportsError`,
  `loadFileMissingFileLeavesDocumentUntouched` and `loadFileNoArgumentReportsError` assert only that the document is
  unchanged — never that an error was reported — and `atomicWriteOldFileSurvivesFailure` injects no failure and would
  pass with the atomic write deleted. They are the same mistake `\` shipped with: the assertion sits somewhere other
  than the seam the user touches.
- **No server→client channel exists.** `viewWasChanged` is the only outbound address (`MainVerticle.java:58`), it carries
  no command field, and the Rust client *drops* any frame whose address is not it (`protocol.rs:60-62`). Copy-out needs a
  new address in both directions, plus a client-side dispatcher.
- **The browser sends no paste at all.** `visur.js` listens for `keydown` and nothing else. `pasteWasReceived` exists,
  and only the terminal ever sends it — crossterm's `Event::Paste` (`main.rs:176-180`). So "paste text from another
  application into the browser page" does nothing today, and fixing it needs no new protocol: a `paste` event handler
  and one frame.
- **The model no longer conflates document and window.** [03](issues/03-document-session-split.md) landed: `Document`
  is the text, its path, its undo ring and its line offsets; `Session` is the cursor, selection, count, submode, scroll
  window, command line and message of one connection; `EditorModelCoupler` is a 94-line locator that hands out
  `document()` and `session()`. `EditorModelKey` and its shared `LocalMap` are deleted, and so is
  `EditorContentService`, which existed to read that map on behalf of a caller holding it.
  **Consequence for the rest of this map: there is a place to put a field, and it is a question with an answer.**
  Dirty baseline ([04](issues/04-dirty-tracking.md)) is the document's; the selected register
  ([09](issues/09-register-model.md)) is the map's decision to put in `Registers`, which 03 did not create because
  nothing writes one yet.
- **No document has an unsaved-work concept.** There is no dirty flag, no `isDirty`, nothing `modified`; and the swap
  file is named after `sourceFilePath`, so **a document that has never been saved gets no swap at all** — the one case
  where losing work is most likely has no safety net.
- **Recovery is silent.** `InitializerService.recoverFromSwapFiles()` reads the swap into the buffer, writes a
  `.visur-recovery` copy and deletes the swap, all without a word to the user, who finds a buffer that differs from disk
  and no explanation.
- **`;`→`:` is four code sites, not a sweep:** the one check in `KeyWasPressedVerticle`, the visible prompt character in
  `visur.js:124` (`let commandStateDisplay = ";"`) and `terminal/src/screen.rs:79`, and two lines of `keys.md`.
  `terminal/src/keys.rs:79` asserts `;` maps verbatim and stays true either way. **Neither `:` nor `|` is bound today.**
- **`;` has never been tested through a keypress.** `CommandStateServiceTest` calls `executeCommandState` directly, so
  the key that opens command state is untested — the exact hole that let `\` ship bound to a two-character token.
  Whatever character wins, it gets a keypress test.
- **`[` and `]` are submodes, not key pairs** (`"quantumStart" pushSubmode`), so `|` + digit follows an existing
  precedent: a submode whose keymap binds `1`–`9`, where a digit cannot be misread as a count.
- **One path in, and it checks loopback.** `/ws` refuses non-loopback peers, and since ticket
  [02](issues/02-viewport-per-client.md) the browser uses that same socket: the SockJS bridge that did not check is
  deleted. What is left for [08](issues/08-clipboard-transport.md) is the policy question — whether loopback is still
  the right boundary once a socket can write the user's clipboard — not a missing check.
- **Skills to consult**: `tdd` throughout — 01, 02 and 04 are small at existing seams, 03 is the one where the suite is
  the only test, 08 and 10 need the two clients. `codebase-design` for 03, which is a pure interface question: what is a
  `Document` that a `Session` is not, and does anything in the current 57 call sites disagree with the answer.
  `wiki-lint`-level doc care in 05, which changes `keys.md`, both clients' prompts and every wayfinder doc that types `;`.

## Decisions

<!-- settled in the grilling session of 2026-11-09, before any ticket was written -->

- **Errors surface on screen.** A message slot in the view, cleared by **any keypress or paste** — including a failed
  save. One surface, one lifetime: the message says what went wrong the last time the user asked for something, and the
  moment they ask for anything else it is stale. There is deliberately no sticky message, because the key that would
  clear it is the key the user is pressing anyway.
- **The document/session split is this map's own ticket**, because dirty baseline, path, swap identity, undo history and
  the selected register are all per-document or per-window state and every one of them is added by this map. Bolting them
  onto the singleton means the screens map deletes them later. Accepted cost: 57 classes.
- **Per-client state is adopted at dispatch, not threaded through it.** A connection is a socket, the server mints its
  id, and the socket stamps that id on every frame it relays; the handler that runs the frame adopts it
  (`emc.putConnection(Connections.headerOf(event))`) so an operator deep in a command reads the geometry of the client
  whose key it is handling. Ticket [02](issues/02-viewport-per-client.md) found the pattern with two fields at stake;
  [03](issues/03-document-session-split.md) extends it to the rest of the session. It is dynamic scope, and what keeps
  it honest is that the ambient thing is an id and the registry has no argument-less getter.
- **One transport: `/ws`, for the browser too.** The SockJS bridge at `/eventbus` could not carry a connection id, so
  the page moved to the socket the terminal already used and the bridge was deleted — which also closed the server's
  only unauthenticated way in. Clipboard work in
  [08](issues/08-clipboard-transport.md) therefore has one protocol to grow, not two.
- **Multiple OS windows are out of scope; in-app screens are the goal.** Registers are therefore **global**, because
  there is one terminal emulator. Sessions exist so the viewport and the cursor stop fighting, not to give each window
  its own clipboard.
- **Registers:** an **unnamed** register is what `y`/`d` write and `p` reads unless the user selects; **`|0` is the
  system clipboard**; **`|1`–`|9` rotate** — every yank, delete *and paste-displacement* overwrites 1 and pushes older
  values down, 9 falling off; **`|a`–`|z` are named** and nothing rotates them, which is what "preserve this" means;
  selection is **sticky**; and a **freeze toggle** stops 1 from being overwritten for anyone who wants the rotation off
  without picking a letter each time.
- **`p`'s displaced text follows the same register rule as everything else**, including when the selected register is the
  system clipboard — so pasting over a paragraph can put text you never copied into your real clipboard. Accepted
  knowingly: a register you paste from that is not the register you paste into is worse.
- **One clipboard write per command**, never per keystroke: `3 d` is one OSC 52 write, not three.
- **The clipboard is a protocol, not an OSC 52 call.** The server never touches an OS clipboard; it asks the *client*.
  The browser can read and write; the current terminal client can write (OSC 52) and mostly cannot read; visur's own
  terminal emulator, which is where this is going, does both. Designing for the emulator we do not have yet is a
  deliberate choice, and the transport limit is documented rather than hidden.
- **`|0` + `p` must fetch an externally copied clipboard**, chosen over "paste with the terminal's own paste key instead".
  The server caches what it last wrote, so the read is only needed when the clipboard changed outside visur; when the
  transport cannot answer, that shows the message from [01](issues/01-message-surface.md) instead of pasting the wrong
  thing quietly.
- **Dirty is a comparison, not a flag.** The document remembers the content as saved and dirty means the two differ, so
  `d` then `u` is **not** dirty. A flag would show a warning on a document that matches the file, which is a lie about
  the one thing the marker exists to tell you.
- **The command key becomes `:` and the commands become vim's names** — `:w`, `:w path`, `:open path`, `:q`, `:q!` — in
  one commit, no dual bindings. `ZZ` survives as a key: a key and a command are different surfaces. Bare paths replace
  quoted literals, because a path is the only argument the editor takes.
- **Nameless documents get a swap; nameless swaps never announce themselves.** `.swa`…`.swz` then `.swa1`… in the
  terminal's cwd, or `~/.local/state/visur/browser/` for the browser; never overwritten, left until the user deletes
  them, never auto-recovered, never prompted for, deleted by the first successful save and by `:q!`. A **named** file with
  a `.<name>.sw*` counterpart gets vim's three-way prompt: recover, delete it, or open read-only.
- **`visur path` passes the path only when the launcher starts the server**, and warns when a server was already running.
  Telling a live server to load a file is how an idle background server eats an unrelated document.
- **SSH is how you are remote**: the client connects over SSH; the server is not exposed to the network. See the one
  transport above for what made that true of the browser too.

## Tickets

| # | ticket | blocked by |
|---|---|---|
| ~~01~~ [closed](issues/01-message-surface.md) | Errors reach the screen: a message slot in the view, retired by any keypress or paste | — |
| ~~02~~ [closed](issues/02-viewport-per-client.md) | Viewport per client: `canvasWidth`/`canvasHeight` stop being global; fixes a live two-client bug | — |
| ~~03~~ [closed](issues/03-document-session-split.md) | The split: `Document` owns content/path/undo/swap, `Session` owns cursor/quantum/span/viewport/mode/command line | 02 |
| ~~04~~ [closed](issues/04-dirty-tracking.md) | Dirty is a comparison against the saved baseline; `*` in the mode line; undo back to saved clears it | 03 |
| [05](issues/05-command-key-and-grammar.md) | `:` replaces `;`, bare paths, `:w` `:open` `:q` `:q!`, and the keypress test `;` never had | 01, 04 |
| [06](issues/06-swap-files.md) | Swap files: nameless pool, browser state dir, never overwrite, delete on save, vim's three-way prompt for named files | 01, 04, 05 |
| [07](issues/07-launch-argument.md) | `visur /path/to/file`, with a warning when a server is already running | 05 |
| [08](issues/08-clipboard-transport.md) | Clipboard transport: outbound write request, inbound contents, server-side cache, one write per command | 01 |
| [09](issues/09-register-model.md) | `|` selects a register: `|0` clipboard, `|1`–`|9` rotation, `|a`–`|z` named, freeze toggle | 03, 08 |
| [10](issues/10-browser-clipboard.md) | Browser clipboard both ways: the `paste` event visur never wired, `writeText`, `readText` | 08 |

**01 changed one thing about this table while it was open.** The map decided a failed save should outlive the next
keypress; the user overrode it — any key or paste retires any message — so the message is one string with one lifetime and
there is no stickiness anywhere in the design. 01 also found that `press` handed the `;` that opens command state to the
command line it had just emptied, so **no command typed from a keyboard had ever compiled**, and fixed it; that is why 05
now finds its keypress test already written.

**03 changed one thing about this table while it was open.** It did not create `Registers`. The map decided the
selected register is global rather than per window, so it is neither of the two owners 03 made, and inventing a third
module with one field in it would have been a decision about rotation disguised as a class; [09](issues/09-register-model.md)
creates it with the rotation rules that justify it. 03 also found that `MainVerticle.start` completed when the verticle
was constructed rather than when the port was listening, which is a load-dependent "connection refused" in any test that
deploys the server and connects; that is fixed, and it is the same hole [07](issues/07-launch-argument.md) reports
through.

**04 found a bug while it was being checked live, and it is not a dirty bug.** `ZZ` had never saved anything after an
insert session: the op that restores the saved cursor quantum peeked at the command stack instead of popping, and
`SaveFileOp` — which takes its path argument off that same stack — cast the leftover to a filename, failed on a null
parent, and died in the verticle without a repaint or a message. Fixed at the op; the live check is now a test. The
lesson for [05](issues/05-command-key-and-grammar.md) is the design under it: **the execution stack is shared scratch
that any command can leave words on, and two file commands read arguments out of it**, so any leftover is a filename.
05's grammar is where that gets settled, and `clearEDS` (used by exactly two commands today) is the precedent it will
either generalize or throw away.

## Not yet specified

- **Screens, tabs, and several documents in one view.** This map builds `Document` and `Session`; it does not decide what
  a screen is, how one is assigned to a document, or what happens when two sessions open the same document at once. The
  last of those is not hypothetical: `:open` from two windows onto one file, then both saving, is a lost write.
- **The document-list screen as a quantum over documents.** The user's description — a screen of documents you navigate
  with the same keys, and can copy, paste or delete as units — is the quantum idea one level up, and the same shape as the
  `json` spec the last map left as fog. It is where [06](issues/06-swap-files.md)'s list of nameless swaps eventually
  wants to be displayed instead of printed.
- **Per-document undo across sessions.** The ring is per `Document` after [03](issues/03-document-session-split.md);
  whether a second session editing the same document sees the first one's undo history is undecided.
- **visur as its own terminal emulator.** [08](issues/08-clipboard-transport.md) is designed as if it exists. The
  emulator itself, and everything else that gets better once we own it (real clipboard read, no OSC 52 beeping, no chord
  dropping, a prompt that can render a list), lives there.
- **What else `:q!` throws away**, once there are things to throw away: registers, command history, other documents.
- **Named-file conflict on save** — another program changed the file since it was opened. Deliberately left out of
  [05](issues/05-command-key-and-grammar.md): it needs the saved mtime or hash, and an "overwrite anyway" key.

## Out of scope

- **Multiple OS windows**, multiple terminal emulators, or a clipboard per window. One terminal emulator is the premise
  for registers being global; if that changes, so does `|0`.
- **Primary selection (X11 middle-click), images, files, and any clipboard payload that is not text.**
- **Automatic upload/backup beyond the swap file**, git integration, and any recovery UI. Recovery of a *named* file is a
  prompt, not a screen.
- **Keymaps as a configurable layer.** `:` replaces `;` and `|` is taken for registers; both are edits to a fixed keymap.
  User-configurable keys remain the map the last session deferred.
- **A general chord vocabulary.** Clipboard keys are ordinary characters, so the terminal's deliberate
  drop-every-CONTROL/ALT/SUPER-chord rule is untouched.
