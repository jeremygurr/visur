#!/usr/bin/env bash
# Fill the conformance table (ticket 09): for each document size, one fresh server, then
#   - what a keystroke costs on the wire        (/ws, the payload contract)
#   - what it costs the canvas in a real window (VISUR_HEADED=1)
#   - what it costs the terminal                (the pty check measures its own paint)
#
#   ./harness/sweep.sh                  # the whole table
#   ./harness/sweep.sh 1048576          # one document size
#
# The server is restarted per document size because VISUR_CONTENT_BYTES is read at startup, and because the editor
# model is shared: a document left mid-edit by a previous run makes the next measurement a different measurement.
set -uo pipefail

ROOT=$(cd "$(dirname "$0")/.." && pwd)
HARNESS="$ROOT/harness/target/release/visur-harness"
JAR=$(ls "$ROOT"/build/libs/visur-*-fat.jar 2>/dev/null | head -1)
LOG=${VISUR_LOG:-/tmp/visur-sweep-server.log}
SIZES=("$@")
[ ${#SIZES[@]} -eq 0 ] && SIZES=(102400 1048576 5242880)

stop_server() {
  local pids
  pids=$(lsof -ti tcp:8888 2>/dev/null) || true
  [ -n "$pids" ] && kill $pids 2>/dev/null
  for _ in $(seq 1 40); do
    lsof -ti tcp:8888 >/dev/null 2>&1 || return 0
    sleep 0.25
  done
}

start_server() {
  ( VISUR_CONTENT_BYTES="$1" java -jar "$JAR" >"$LOG" 2>&1 & )
  for _ in $(seq 1 120); do
    curl -sf -o /dev/null --max-time 1 http://127.0.0.1:8888/static/visur.html && return 0
    sleep 0.5
  done
  echo "the server did not come up with VISUR_CONTENT_BYTES=$1 (see $LOG)" >&2
  return 1
}

[ -x "$HARNESS" ] || { echo "build the harness first: cargo build --release --manifest-path harness/Cargo.toml" >&2; exit 2; }
[ -n "$JAR" ] || { echo "build the server first: ./gradlew shadowJar" >&2; exit 2; }

printf '%-9s %-8s %s\n' "document" "renderer" "result"
for bytes in "${SIZES[@]}"; do
  stop_server
  start_server "$bytes" || exit 1
  label=$((bytes / 1024))"KB"

  # Once per server: the contract checks are viewport- and document-independent.
  "$HARNESS" publish | sed 's/^/'"$label"'    /'

  for viewport in "112 45" "200 60"; do
    read -r cols rows <<<"$viewport"
    printf '%-9s %-8s ' "$label" "canvas"
    "$HARNESS" ws --cols "$cols" --rows "$rows" | sed 's/^ws        //' | tr '\n' ' ' | sed 's/  */ /g'
    echo
    printf '%-9s %-8s ' "$label" "browser"
    VISUR_HEADED=1 "$HARNESS" browser --cols "$cols" --rows "$rows" | sed 's/^browser   //' | tr '\n' ' ' | sed 's/  */ /g'
    echo
  done

  for viewport in "100 30" "250 70"; do
    read -r pty_cols pty_rows <<<"$viewport"
    printf '%-9s %-8s ' "$label" "term${viewport// /x}"
    VISUR_PTY_COLS=$pty_cols VISUR_PTY_ROWS=$pty_rows VISUR_E2E=1 \
      cargo test --release --quiet --manifest-path "$ROOT/terminal/Cargo.toml" --test pty the_client_paints \
      -- --nocapture --test-threads=1 2>/dev/null | grep -o 'median [0-9.]* ms' | head -1
  done
done
stop_server
