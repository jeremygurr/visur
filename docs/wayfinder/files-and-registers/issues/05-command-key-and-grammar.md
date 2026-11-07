# `:` replaces `;`, and the commands get vim's names

Type: task
Status: open
Blocked by: 01, 04

## Work

Command state is opened by `;` and takes a quoted literal first: `;"path" loadFile`. Both halves are wrong for a user who
knows vim, and neither has a documented reason behind it — `keys.md:55` states the key and no map anywhere argues for it.

**Rename the key in one commit.** Four code sites, all found: the one check in `KeyWasPressedVerticle` that sets
`isInCommandState`; the visible prompt in `visur.js:124` (`let commandStateDisplay = ";"`); the same prompt drawn by the
terminal in `terminal/src/screen.rs:79`; and `docs/reference/keys.md` (the key row and the section heading at `:112`).
`terminal/src/keys.rs:79` asserts `;` maps verbatim, which stays true and stays uninteresting. `:` is bound to nothing
today, in either client. No dual binding and no compatibility window: that is the coding standard, and it is right here,
because two keys for one entry point is two keys to document and test forever.

**`:` then `w`, `open`, `q`, `q!`.** `:w` writes the remembered path; `:w path` writes there and adopts it as the path
(this is `saveFile`'s existing "explicit path updates source" behaviour, already tested); `:open path` loads a file.
`:q` refuses when [04](04-dirty-tracking.md) says dirty, and the refusal is [01](01-message-surface.md)'s message
naming what to type instead. `:q!` quits and **discards the nameless `.sw*`** of the current document
([06](06-swap-files.md) owns that file's life; this ticket owns the command that ends it). `ZZ` survives as a key — a key
and a command are different surfaces, not a dual binding. `:e`, `:saveas`, `:wq` and the rest stay out.

**Bare paths.** Verified today: `loadFile /tmp/x.txt` compiles to `LoadFileOp` **alone** — `LiteralStringWord`'s regex
only matches a leading quote, so the path token never compiles and the operator runs with no argument. A word matcher that
takes the rest of the line, tried only after a command name has matched, turns `:open ~/notes/ideas.txt` into a path.
Quotes then mean exactly one thing: a string that contains a trailing space.

**A typo is currently not an error, it is a variable recall.** `frobnicate` compiles to zero ops and reports nothing;
`3 span` compiles to a number and a `RecallOp`, because `RecallWord` is the catch-all at the end of the matcher list and
an unrecognised word becomes a global-variable lookup. Any command name this ticket introduces has to either match or
say so, which is a rule about the matcher list rather than about these four commands.

## Done when

- [x] `:` opens command state and both clients draw `:` as the prompt. `;` opens nothing, and it is gone from `keys.md`.
- [x] **A keypress test presses the key.** `CommandStateServiceTest.theKeyThatOpensCommandStateIsNotPartOfTheCommand` exists
  since ticket 01 and presses it for real — it is what caught `press` handing the `;` to the command line it had just
  emptied, so every command typed from a keyboard compiled to nothing. Retarget it at `:` and keep pressing the key, rather
  than going back to calling `executeCommandState` directly.
- [x] `:w`, `:w path`, `:open path`, `:q`, `:q!` each have a test at the seam a human uses: the sentence goes through
  `CommandCompileService`, not into an operator by hand. Every existing `FileIoTest` test builds its command directly,
  which is how the bare-path breakage stayed invisible.
- [x] `:open` on a dirty document refuses, with the reason on screen; `:open` on a clean one loads.
- [x] `:w` on a document with no path reports **`Error writing: No filename provided`** and leaves the document dirty.
- [x] `:q` on a dirty document refuses; `:q!` exits. Both asserted.
- [x] `:w` with no argument on an unnamed document is distinguishable from `:w` with an argument, in the compiled command, not
  only at execution time.
- [x] An unknown command (`:frobnicate`) produces a visible message naming the command, not zero ops.
- [x] Command history (`ArrowUp` through the last 20, `CommandStateService`) still recalls commands typed with `:`, and the
  history is asserted to survive the rename.
- [x] `keys.md` documents `:`, the four commands, bare paths, and `;`'s absence — a user with the old key in their fingers
  deserves to find out in one line where it went.

## Notes

- **A path containing a space is the sharp edge.** "Rest of the line" gets `"~/My Docs/x.txt"` free; a trailing space does
  not survive, and trimming must be stated in a test rather than discovered as a bug report.
- `CommandCompileService.compile`'s loop breaks out of everything when no matcher fires, so anything appended after an
  unparseable token is silently discarded. Bare paths need to be matched *before* `RecallWord` has swallowed the tail.
- `:q` needs to reach the shutdown path the launcher already understands — `ZZ`/`ZQ` exit with code 0 to mean "leave the
  server up", so `:q` has to say which of the two it means or it will strand or kill a server by accident.
- `visur.js` has no test harness, which is why the prompt change is asserted in `screen.rs` and the wire contract, and
  why this ticket's own risk is the browser half.
- **Three things the matcher list has to leave better than it found them**, all verified while 04 was being checked:
  - `NativeOpWord` prints `operator X not recognized.` to stdout for any word it does not know, and it is tried before
    `RecallWord`, so a legitimate recall prints noise on the way to working. It fires twice per boot from the `n`/`N`
    keymaps. Ticket 01 made an invisible failure the bug; this one is invisible because it goes to a log nobody reads.
    Whatever 05 decides about unknown words, the print either becomes the message or goes away.
  - `RecallWord` reads `session().getGlobalVarMap()` while compiling. Keymaps compile at boot, so a keymap word naming a
    per-session variable resolves against whichever session existed at boot, or not at all, and the compiled command is
    empty for the windows that should have had it. A bare-path matcher must not do the same thing: match on shape,
    resolve at execution.
  - **The keymap analogue of the typo rule.** In the `changeScope` submode (`/`), any key that is not a quantum key pops
    the submode and returns to navigate with no message: `/` `x` is a silent mode change, and the `x` is spent. Users
    type `/foo` as vim search and land in whatever the letters happen to bind to next. 05's rule — a word that does not
    match says so — is the same rule one level up, and the submodes need it as much as the command line does.
- The command names are user-visible API. `:open` and not `:e` is the user's call, made deliberately, so that when `:e` is
  added someday it means something else rather than being a synonym nobody can remove.

## What landed

**The key, in one commit, with no dual binding.** Four sites: the check in `KeyWasPressedVerticle`, the prompt
`visur.js` draws, the prompt `screen.rs` draws, `keys.md` — plus the pty test that types the key and asserts `:x` reaches
the screen, and one added assertion in `keys.rs` that `:` and `!` arrive as themselves. `;` now does nothing at all, and
a test insists on the *nothing*: not command state, not a character in the line, not a mode change.

**`:w`, `:w path`, `:open path`, `:q`, `:q!`.** Bare paths, quotes only for spaces, trailing spaces trimmed, an unknown
word quoted back, `Error writing: No filename provided` as the map asked for it, `:open` refusing over unsaved work, `:q`
refusing and naming `:q!`, `:q!` discarding the swap file so the discarded work does not come back next boot.

**One deviation from the ticket's sketch, and it matters.** The plan was a word matcher that takes the rest of the line,
tried after a command name has matched. A matcher cannot know that: `CommandCompileService.compile` has no context about
what came before, so such a matcher placed before `RecallWord` would claim *every* unrecognised word in *every* keymap
sentence — `frobnicate` in a keymap becomes a string literal pushed on the stack instead of an error, which is the bug the
ticket was written to fix, wearing a different hat. So the command line got its own grammar instead:
`CommandCompileService.compileCommandLine`, a name and the rest of the line, run by `CommandStateService` while keymaps
keep using `compile`. Two grammars for two surfaces that were never the same thing — one is a language a user types, the
other is the editor's own sentences.

**`clearEDS` generalised, for the command line only.** Every `:` command starts by clearing the scratch stack, because a
command a human just typed is a fresh utterance and `SaveFileOp` reads its path off that stack. Keymaps still balance
their own sentences; that is a rule about compiler-generated code, and 05 does not change it.

**`:q` means the window, not the server.** The Notes asked which of the two it means, and the answer is the one that cannot
strand anybody: the socket that asked closes with the protocol's normal-closure code, the terminal reads that as a detach,
exits 0, and the launcher leaves the server running — so `:q` and `ZQ` are the same door. Stopping the process stays
`ZD`'s, because the launcher started it, a browser tab cannot see the process it would be ending, and a command that ends
other windows' sessions is a footgun with a colon in front of it. Three places had to agree: the op sets a flag on the
session (an op has no bus and `press` has no socket), `handle` spends it on the way out, published with the connection
header exactly as a view is; the owning socket closes itself; and the client tells a close frame apart from a socket
failing, which it never needed before — a close frame with *no* status is not a farewell, since a peer that hangs up
without a reason has not asked to be left running.

**The raw sentence door is gone, deliberately.** `;` used to take `;"word" quantumStart`. The command line is a command
language now, so the escape hatch from the keyboard is `:w`/`:open`/`:q` and everything else is a key. Nothing in the repo
used the sentence door except a `keys.md` example, and a debug surface that a user can fall into is not a debug surface.

**Tests, at the seam a human uses.** `CommandLineTest` (15) types `:`, the characters, and `Enter`, then reads the disk,
the screen and the flag; the compiled-command claim is asserted at the compile level because that assertion *is* about the
compiled command. `SessionsOnOneDocumentTest` types `:q!` on one socket and asserts that socket closed while the other
kept being painted. `the_client_detaches_when_the_server_lets_this_window_go` drives a real pty against a fake server that
closes normally and asserts exit code 0 — it reports exit 4 without the client change, which is the launcher contract
actually being tested. `CommandStateServiceTest` presses `:` for real, keeps the history assertion across the rename, and
its "Enter executes" test no longer types an editor sentence. Three grammar mutations were checked: no `clearEDS` head, no
trim, no quote-strip — each reddens exactly one test.

**Five fixtures lost their load.** They typed junk into a document and then loaded a file over it, which is now refused;
each says so in a comment rather than quietly calling `putContentAsSaved`.

**Checked live**, because the browser half has never had a harness: `:q` refused while dirty naming `:q!`; `:w` then `:q`
closed the socket with code 1000 and reason `:q`; `:q!` deleted `<file>.visur-swap` from disk and left the document's file
holding the text it held before. `:frobnicate` printed itself on the mode line.

## Left alone

- `:open` overwrites the file's identity but keeps no record of the mtime, so a file changed by another program is silently
  replaced on the next save. The map already keeps that out of scope.
- `:w` does not accept `~` expansion beyond what the shell would have typed; the server resolves the path as given, plus
  `toAbsolutePath` for a bare name. A path typed as `~/x` arrives as the two characters `~/` and becomes a directory
  called `~`. Said here because it will surprise exactly one person, who will then ask for it properly.
- Command history is per server, not per window — it was a field on `CommandStateService` before the split and stayed
  there. `:` `ArrowUp` in window two offers window one's commands. It is a small leak, and the right owner is the window.
