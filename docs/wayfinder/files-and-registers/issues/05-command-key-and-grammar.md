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

- `:` opens command state and both clients draw `:` as the prompt. `;` opens nothing, and it is gone from `keys.md`.
- **A keypress test presses the key.** `CommandStateServiceTest.theKeyThatOpensCommandStateIsNotPartOfTheCommand` exists
  since ticket 01 and presses it for real — it is what caught `press` handing the `;` to the command line it had just
  emptied, so every command typed from a keyboard compiled to nothing. Retarget it at `:` and keep pressing the key, rather
  than going back to calling `executeCommandState` directly.
- `:w`, `:w path`, `:open path`, `:q`, `:q!` each have a test at the seam a human uses: the sentence goes through
  `CommandCompileService`, not into an operator by hand. Every existing `FileIoTest` test builds its command directly,
  which is how the bare-path breakage stayed invisible.
- `:open` on a dirty document refuses, with the reason on screen; `:open` on a clean one loads.
- `:w` on a document with no path reports **`Error writing: No filename provided`** and leaves the document dirty.
- `:q` on a dirty document refuses; `:q!` exits. Both asserted.
- `:w` with no argument on an unnamed document is distinguishable from `:w` with an argument, in the compiled command, not
  only at execution time.
- An unknown command (`:frobnicate`) produces a visible message naming the command, not zero ops.
- Command history (`ArrowUp` through the last 20, `CommandStateService`) still recalls commands typed with `:`, and the
  history is asserted to survive the rename.
- `keys.md` documents `:`, the four commands, bare paths, and `;`'s absence — a user with the old key in their fingers
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
- The command names are user-visible API. `:open` and not `:e` is the user's call, made deliberately, so that when `:e` is
  added someday it means something else rather than being a synonym nobody can remove.
