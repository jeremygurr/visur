# `visur /path/to/file`

Type: task
Status: open
Blocked by: 05

## Work

The only way to open a file at launch is `VISUR_FILE=/path/to/file ./visur`, and it works **only when the launcher starts
the server**: `visur` reuses a server already answering on 8888 (`probe()` curls the app's own URL), and that server read
the environment variable before this one existed. So `VISUR_FILE=x ./visur` twice in a row silently opens `x` the first
time and something else the second, which is the worst shape a flag can have.

**`visur /path/to/file` becomes the way, and `VISUR_FILE` goes away** — one path in, no environment variable, no second
route to the same behaviour. The launcher passes it to the server it starts; the server treats it exactly as `:open`
would, so there is one load path and the source path, dirty baseline and swap identity are set by the code that already
sets them.

**When a server is already running, warn and do nothing.** The launcher's whole design is "use the server that is already
there", and that server is editing somebody's document. `:open`-ing the requested path into it is how an idle background
server eats an unrelated file, and the warning is the honest answer:

> visur is already running on :8888 with another document — your file was not opened. `:open path` inside it, or stop it
> first.

**Reading a file at launch is not a place where a prompt belongs.** If the file has a `.<name>.sw*` counterpart, the
prompt from [06](06-swap-files.md) still fires, because that is a conflict about *this* file and the user is present by
definition. What must not happen is the old silent recovery: `visur notes.txt` starting with `notes.txt`'s contents
replaced by a swap nobody chose.

## Done when

- [x] `visur notes.txt` from a shell in any directory opens that file, with the path resolved against the **shell's** cwd
  (the launcher must resolve it before handing it on, or it becomes the server's cwd and surprises someone).
- [x] `visur` with no argument behaves exactly as today, including connecting to a running server and sharing its document.
- [x] `visur path` with a server already running prints the warning, exits without a nonzero-status surprise, and opens
  nothing. Asserted by a test of the launcher script — `visur` already tests its own lifecycle by overriding
  `VISUR_SERVER` and `VISUR_CLIENT`, so the pattern exists.
- [x] A path that does not exist opens an empty document at that path, which is `LoadFileOp`'s current "file not found" error
  path and therefore **changes today's behaviour**: launching on a missing file must become "new file at that path", and
  `:open` on a missing file stays an error. The two are different questions and the tests say so separately.
- [x] A path that is a directory, or unreadable, fails with [01](01-message-surface.md)'s message rather than a stack trace
  in the log.
- [x] `VISUR_FILE` is gone: no reader, no documentation, no test. `README.adoc`/`keys.md` gain `visur path`.
- [x] A path that looks like a flag (`visur --help`, `visur -x`) does not become a filename. One test, whatever the answer
  turns out to be.
- [x] Starting a second server against a busy port **ends the second process**, with the reason on its own output. Asserted by
  starting two and counting the JVMs afterwards, because "it exits" is the difference between an orphan and a pile of
  watchers that no `lsof` can find.

## Notes

- `visur` is a bash script with a deliberate contract, documented in its own header: it starts a server only when none is
  up, stops a server it started unless the client exits 0 (`ZZ`/`ZQ` mean "keep it"), and never stops one it did not
  start. The file argument does not change that contract and must not quietly start changing ownership of the server.
- The server is a fat jar started with a JVM; the argument has to survive `./visur` → `gradlew run` → `MainVerticle` →
  `InitializerService.startupFile()` (`:243`), which is the code reading the env var today. Passing it as a **JVM
  argument** rather than an environment variable is the point.
- The launcher's fallback path is `./gradlew run`; a file argument needs to reach the JVM through *both* start paths or the
  bug will only appear for whoever takes the fallback.
- **Servers pile up, and `lsof` cannot see most of them.** Found 2026-11-05: nine visur JVMs on a machine, four of them
  `--redeploy` watchers from Mon/Wed, one listener. `MainVerticle.start` calls `listen(serverPort())` and never looks at
  the result (`:43`), so a server that cannot open 8888 starts no HTTP server, says nothing, and — because `gradlew run
  --redeploy` keeps a JVM alive to watch `src/**` — sits there forever burning CPU (45 CPU-minutes each in that `ps`).
  `probe()` then succeeds against the *real* server, so `./visur` reports "using the server already on :8888" and connects
  to whoever owns the port while the corpses accumulate. `stop_owned` kills `$owned` plus `lsof -ti tcp:$PORT`, which
  reaches the listener only: the watchers have no socket. So the fix is upstream of the script — **a server that cannot
  bind its port must exit**, which makes every one of these orphans impossible rather than killable — and the launcher
  wants a way to stop a server it does not own (`visur stop`, or refusing to start a second server when asked for one).
  For a human today: `lsof -ti tcp:8888 | xargs kill` then `pkill -f com.ple.visur.MainVerticle`.
- **Future, deliberately not this ticket:** `visur path` with a server running is obviously "open a window/screen on this
  document", which is the multi-document-in-one-view fog. The warning is the placeholder for that feature, and it is
  written so that replacing it later changes one message and not a protocol.

## What landed

**The launch load happens when the first window arrives, not at boot.** Boot's answer to a launch is either a question
about a swap or a sentence about a file, and both are things a *window* is told; a server with no client has nowhere to
put them. So `InitializerService` holds the path until an arrival takes it, and the arrival runs `LoadFileOp.open` — the
same door `:open` runs, which is why the directory case stopped throwing and started speaking. The first window to
connect is the person who typed the command, because the launcher refuses to hand a file to a server it did not start,
so "the user is present by definition" is not wishful thinking; it is the launcher's own rule.

**The two doors differ by one boolean.** `open(emc, window, path, missingIsANewFile)`: launched on a path that is not
there, you are about to write it; `:open`-ing it, you mistyped it. That is the ticket's "different questions", and it is
one parameter rather than two functions, so the swap check, the read, and the whole `install` reset cannot drift apart.
`window` is a parameter rather than `emc.session()` because an arrival has no adopted connection yet — the same reason
`startSession(Session)` already takes one.

**The latch is taken, not read, and it lives on `InitializerService` rather than the document** — it predates the
document, since the open is what gives the document its name. Synchronised, because two windows can arrive on two
event-loop threads and a launch that runs twice is a file loaded over somebody's buffer.

**Two start paths became one.** The launcher no longer falls back to `./gradlew run`: that starts a `--redeploy`
watcher, a JVM with no socket, which outlives its client and is invisible to the `lsof` that stops what the launcher
owns — and it was the second route a `-Dvisur.file` would have had to reach. It builds the fat jar when any source file
is newer and runs `java -jar`, so the argument has one place to get to and a watcher-shaped orphan is impossible rather
than killable. Fourteen of them were running on this machine the day the decision was made, none holding a port, and
`stop_owned` had never seen them. `./gradlew run` stays the way to run a watching server, and says so in its own README
line in `README.adoc`.

**Exit codes are now written down**: the client's, `1` for "your file was not opened", `2` for a command line visur does
not take. `--help` is refused rather than implemented — the ticket asked for one test "whatever the answer turns out to
be", and the answer is that a flag is not a file and there is no help to reach yet.

**Tests.** `tests/launcher.sh` +4 (a file against a running server: warning, exit 1, no client ran, the server
untouched; the resolved absolute path read off a stub server that records its arguments, run from a subdirectory with a
relative path; a flag refused without starting anything; a second real server on a busy port exiting with the reason on
its own output). `StartupFileTest` gained the launch cases: swap → the first window is asked and nothing is chosen for
it, missing path → an empty document *at that path* whose `:w` lands there, directory → a sentence on the window and a
keyboard that still works, unreadable file → the same sentence, and a late arrival told nothing and clobbering nothing.
Two of those were green when written, which is what the shared door buys, and one taught something:

**The pin test was wrong before it was right.** "A window arriving later must not reload the file over the first
window's work" stayed green when the latch was mutated to read-instead-of-take, because by then the first window's
typing had put a swap beside the file, so the second launch found a *conflict* rather than a file to install — the text
survived and nothing was wrong with the assertion I had written. The failure is that the joiner gets **a question about
a launch it did not perform**, and that is what the test asserts now. It is also the only reason the bug was worth
fixing at all: the document's text was never in danger here, an unanswered question in a stranger's window was.

**Checked live**, through the real launcher and the real jar: `visur report.txt` from that file's directory asked the
first window about its swap and loaded nothing; `visur other.txt` with that server running printed the warning naming the
file and exited 1 with the server left alone; `visur --help` exited 2 having started nothing.

**Left alone**: a buffer that never had a final separator saves without one, so `visur new.txt`, typing, `ZZ` writes
`first words` with no trailing newline — vim would write a newline. Found by the new-file test, which asserts where the
bytes landed and strips when checking which bytes they were; no ticket has asked the newline question, so this one does
not answer it either.
