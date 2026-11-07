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
VISUR_CLIENT="$(client_that_says_it_ran 3)" VISUR_LOG="$LOG" "$LAUNCHER" "$SCRATCH/notes.txt" >"$LOG.out" 2>&1
status=$?
check "it exits 1, the documented nothing-was-opened code" "$status" "1"
check "it says the file was not opened" "$(grep -c 'was not opened' "$LOG.out")" "1"
check "it names the way round it" "$(grep -c ':open' "$LOG.out")" "1"
check "no client ran" "$([ -e "$SCRATCH/client-3-ran" ] && echo ran || echo not-run)" "not-run"
check "the running server was left alone" "$(answering && echo up || echo down)" "up"
stop_any_server

echo "a file argument reaches the server as an absolute path"
mkdir -p "$SCRATCH/project"
(cd "$SCRATCH/project" && VISUR_SERVER="$(stub_server)" VISUR_CLIENT="$(client_exiting 3)" VISUR_LOG="$LOG" \
  "$LAUNCHER" notes.txt) >"$LOG.out" 2>&1
check "resolved against the shell's cwd, not the server's" "$(cat "$SCRATCH/stub-args")" \
  "-Dvisur.file=$SCRATCH/project/notes.txt"
(cd "$SCRATCH/project" && VISUR_SERVER="$(stub_server)" VISUR_CLIENT="$(client_exiting 3)" VISUR_LOG="$LOG" \
  "$LAUNCHER") >"$LOG.out" 2>&1
check "and with no argument, nothing is handed over" "$(cat "$SCRATCH/stub-args")" ""
stop_any_server

echo "a flag is not a file"
VISUR_CLIENT="$(client_that_says_it_ran 3)" VISUR_LOG="$LOG" "$LAUNCHER" --help >"$LOG.out" 2>&1
status=$?
check "it refuses" "$([ "$status" -ne 0 ] && echo refused || echo accepted)" "refused"
check "it says what it takes" "$(grep -c 'one file' "$LOG.out")" "1"
check "no client ran" "$([ -e "$SCRATCH/client-3-ran" ] && echo ran || echo not-run)" "not-run"
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
