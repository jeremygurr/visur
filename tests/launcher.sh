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

echo
if [ "$failures" -eq 0 ]; then
  echo "PASS: the launcher starts, reuses and stops the right server"
else
  echo "FAIL: $failures check(s)"
fi
exit "$failures"
