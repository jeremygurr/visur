#!/usr/bin/env bash
# The launcher's contract, checked without a terminal.
#
# `VISUR_CLIENT` replaces the client, so each scenario can choose how the client exits instead of driving a pty:
# exit 3 is quit (stop a server we started), exit 0 is detach (leave it running).
#
#   ./tests/launcher.sh
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PORT=8888
LAUNCHER="$ROOT/visur"
LOG="${TMPDIR:-/tmp}/visur-launcher-test.log"
failures=0
SCRATCH="$(mktemp -d)"
trap 'rm -rf "$SCRATCH"; stop_any_server' EXIT

# Stand-ins for the client, so no pty is needed: the launcher only cares about its exit code.
client_exiting() {
  local script="$SCRATCH/client-$1"
  printf '#!/usr/bin/env bash\nexit %s\n' "$1" >"$script"
  chmod +x "$script"
  echo "$script"
}

url="http://127.0.0.1:$PORT/static/visur.html"
answering() { curl -sf -o /dev/null --max-time 1 "$url"; }

stop_any_server() {
  local pids
  pids="$(lsof -ti "tcp:$PORT" 2>/dev/null)"
  [ -n "$pids" ] && kill $pids 2>/dev/null
  for _ in $(seq 1 40); do answering || return 0; sleep 0.25; done
  return 1
}

client_that_records_its_url() { # a client that writes down the socket it was handed, which is how a file is asked for
  local script="$SCRATCH/client-url"
  {
    echo '#!/usr/bin/env bash'
    echo "printf '%s' "\${VISUR_WS:-}" > "$SCRATCH/client-url-seen""
    echo "touch "$SCRATCH/client-$1-ran""
    echo "exit $1"
  } >"$script"
  chmod +x "$script"
  echo "$script"
}

client_that_says_it_ran() { # a client that leaves a mark, so "did the launcher run me at all?" is answerable
  local script="$SCRATCH/client-marked-$1"
  {
    echo '#!/usr/bin/env bash'
    echo "touch \"$SCRATCH/client-$1-ran\""
    echo "exit $1"
  } >"$script"
  chmod +x "$script"
  echo "$script"
}

# A server that records the arguments it was started with and then answers the probe, so "what did the launcher hand
# the JVM?" is a question with an answer. The app's own URL has to answer 200, which is what the empty static tree is.
stub_server() {
  local stub="$SCRATCH/stub-server"
  mkdir -p "$SCRATCH/web/static"
  : >"$SCRATCH/web/static/visur.html"
  {
    echo '#!/usr/bin/env bash'
    echo "printf '%s ' \"\$@\" | sed 's/ \$//' > \"$SCRATCH/stub-args\""
    echo "exec python3 -m http.server $PORT --bind 127.0.0.1 --directory \"$SCRATCH/web\""
  } >"$stub"
  chmod +x "$stub"
  echo "$stub"
}

check() { # description, actual, expected
  if [ "$2" = "$3" ]; then
    echo "  ok   $1"
  else
    echo "  FAIL $1 (expected '$3', got '$2')"
    failures=$((failures + 1))
  fi
}

echo "cold start, client quits"
stop_any_server
VISUR_CLIENT="$(client_exiting 3)" VISUR_LOG="$LOG" "$LAUNCHER" >"$LOG.out" 2>&1
check "exit code is the client's" "$?" "3"
check "the server it started is stopped" "$(answering && echo up || echo down)" "down"
check "it said it started one" "$(grep -c 'starting one' "$LOG.out")" "1"

echo "cold start, client detaches"
stop_any_server
VISUR_CLIENT="$(client_exiting 0)" VISUR_LOG="$LOG" "$LAUNCHER" >"$LOG.out" 2>&1
check "exit code is the client's" "$?" "0"
check "the server it started is left running" "$(answering && echo up || echo down)" "up"
check "it said so" "$(grep -c 'left running' "$LOG.out")" "1"

echo "warm start, client quits"
VISUR_CLIENT="$(client_exiting 3)" VISUR_LOG="$LOG" "$LAUNCHER" >"$LOG.out" 2>&1
check "exit code is the client's" "$?" "3"
check "a server it did not start survives" "$(answering && echo up || echo down)" "up"
check "it said it reused one" "$(grep -c 'already on' "$LOG.out")" "1"

stop_any_server

echo "server that will not start"
stop_any_server
# `false` stands in for a server that dies before it answers: the launcher must say so, quickly, and leave nothing.
VISUR_SERVER="false" VISUR_CLIENT="$(client_exiting 0)" VISUR_LOG="$LOG" "$LAUNCHER" >"$LOG.out" 2>&1
status=$?
check "fails rather than hanging" "$([ "$status" -ne 0 ] && echo failed || echo continued)" "failed"
check "explains itself" "$(grep -c 'did not come up' "$LOG.out")" "1"
check "leaves no JVM behind" "$(pgrep -f 'com.ple.visur.MainVerticle' | wc -l | tr -d ' ')" "0"

stop_any_server

echo "a file argument, with a server already running"
VISUR_CLIENT="$(client_exiting 0)" VISUR_LOG="$LOG" "$LAUNCHER" >"$LOG.out" 2>&1 # one that stays
: >"$SCRATCH/notes.txt"
VISUR_CLIENT="$(client_that_records_its_url 3)" VISUR_LOG="$LOG" "$LAUNCHER" "$SCRATCH/notes.txt" >"$LOG.out" 2>&1
status=$?
check "the client runs, because a running server is not a reason a file cannot be opened" \
  "$([ -e "$SCRATCH/client-3-ran" ] && echo ran || echo not-run)" "ran"
check "the client's own exit code is what the launcher reports" "$status" "3"
check "the ask went to the window, not to the server" "$(cat "$SCRATCH/client-url-seen")" \
  "ws://127.0.0.1:8888/ws?open=$SCRATCH/notes.txt"
check "no second server was started" "$(grep -c 'starting one' "$LOG.out")" "0"
check "the running server was left alone" "$(answering && echo up || echo down)" "up"
stop_any_server

echo "the file named for a window is the path the command was typed with"
mkdir -p "$SCRATCH/project"
(cd "$SCRATCH/project" && VISUR_SERVER="$(stub_server)" VISUR_CLIENT="$(client_that_records_its_url 11)" VISUR_LOG="$LOG" \
  "$LAUNCHER" notes.txt) >"$LOG.out" 2>&1
check "resolved against the shell's cwd, not the server's" "$(cat "$SCRATCH/client-url-seen")" \
  "ws://127.0.0.1:8888/ws?open=$SCRATCH/project/notes.txt"
# A directory that is not there yet is not a reason to mistranslate the path: `cd` into it fails, and an absolute
# argument still means what it said.
(cd "$SCRATCH/project" && VISUR_SERVER="$(stub_server)" VISUR_CLIENT="$(client_that_records_its_url 12)" VISUR_LOG="$LOG" \
  "$LAUNCHER" "$SCRATCH/yet-to-be/notes.txt") >"$LOG.out" 2>&1
check "an absolute path under a directory that does not exist stays itself" "$(cat "$SCRATCH/client-url-seen")" \
  "ws://127.0.0.1:8888/ws?open=$SCRATCH/yet-to-be/notes.txt"
(cd "$SCRATCH/project" && VISUR_SERVER="$(stub_server)" VISUR_CLIENT="$(client_that_records_its_url 13)" VISUR_LOG="$LOG" \
  "$LAUNCHER" not-written/notes.txt) >"$LOG.out" 2>&1
check "and a relative one is still resolved against where it was typed" "$(cat "$SCRATCH/client-url-seen")" \
  "ws://127.0.0.1:8888/ws?open=$SCRATCH/project/not-written/notes.txt"
# A path is bytes, and the ones that mean something to a URL have to be said in a way that does not.
mkdir -p "$SCRATCH/project/my notes"
(cd "$SCRATCH/project" && VISUR_SERVER="$(stub_server)" VISUR_CLIENT="$(client_that_records_its_url 5)" VISUR_LOG="$LOG" \
  "$LAUNCHER" "my notes/todo list.txt") >"$LOG.out" 2>&1
check "a path is encoded for the query rather than pasted into it" "$(cat "$SCRATCH/client-url-seen")" \
  "ws://127.0.0.1:8888/ws?open=$SCRATCH/project/my%20notes/todo%20list.txt"
(cd "$SCRATCH/project" && VISUR_SERVER="$(stub_server)" VISUR_CLIENT="$(client_that_records_its_url 6)" VISUR_LOG="$LOG" \
  "$LAUNCHER") >"$LOG.out" 2>&1
check "and with no argument, nothing is asked for" "$(cat "$SCRATCH/client-url-seen")" ""
stop_any_server

echo "a flag is not a file"
VISUR_CLIENT="$(client_that_says_it_ran 9)" VISUR_LOG="$LOG" "$LAUNCHER" --help >"$LOG.out" 2>&1
status=$?
check "it refuses" "$([ "$status" -ne 0 ] && echo refused || echo accepted)" "refused"
check "it says what it takes" "$(grep -c 'one file' "$LOG.out")" "1"
check "no client ran" "$([ -e "$SCRATCH/client-9-ran" ] && echo ran || echo not-run)" "not-run"
check "no server was started" "$(grep -c 'starting one' "$LOG.out")" "0"
check "nothing is listening" "$(answering && echo up || echo down)" "down"

echo "a second server on a busy port ends itself"
# The one check that needs the real jar: an orphan is a process that stays, and only a real bind attempt says whether
# it stays. Built by the launcher itself when it is stale, so a checkout with no jar has nothing to check.
jar="$ROOT/build/libs/visur-1.0.0-SNAPSHOT-fat.jar"
[ -f "$jar" ] || (cd "$ROOT" && ./gradlew shadowJar -q >/dev/null 2>&1)
if [ -f "$jar" ]; then
  VISUR_CLIENT="$(client_exiting 0)" VISUR_LOG="$LOG" "$LAUNCHER" >/dev/null 2>&1 # one that stays
  second_out="$(java "-Dvisur.port=$PORT" -jar "$jar" 2>&1)"
  status=$?
  check "it exits rather than watching" "$([ "$status" -ne 0 ] && echo exited || echo ran)" "exited"
  check "it says why on its own output" "$(printf '%s' "$second_out" | grep -c 'cannot serve on port')" "1"
  check "the first server still answers" "$(answering && echo up || echo down)" "up"
  check "and exactly one jar process is left" "$(pgrep -f 'visur-1.0.0-SNAPSHOT-fat.jar' | wc -l | tr -d ' ')" "1"
else
  echo "  skipped: no fat jar and no way to build one"
fi

stop_any_server

echo
if [ "$failures" -eq 0 ]; then
  echo "PASS: the launcher starts, reuses and stops the right server"
else
  echo "FAIL: $failures check(s)"
fi
exit "$failures"
