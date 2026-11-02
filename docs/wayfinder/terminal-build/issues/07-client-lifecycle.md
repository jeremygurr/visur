# Terminal client: capabilities, lifecycle, and quitting

Type: task
Status: closed
Closed: 2026-11-01
Blocked by: 05

## Work

Spec §5.4–5.5, §5.9.

- **Capability probe** at attach: `?2026` via `DECRQM`, kitty `DISAMBIGUATE_ESCAPE_CODES` push, both probe-not-version-check, both degrading silently.
- **Ordered restore**, not RAII: `panic::set_hook` plus a `Drop` guard running `?2026l → ?25h → disable paste → pop keyboard flags → ?1049l → disable_raw_mode()`.
- **`SIGWINCH`** → `viewportWasChanged {cols, rows}` → `full: true` repaint.
- **Quit**: the `Z` prefix — `ZZ` save-and-detach, `ZQ` detach, `ZD` quit — decided 2026-11-01, replacing the earlier `Ctrl-C`/`Ctrl-Q` proposal because those read as "kill this command" in a shell. Detach and quit differ only in the exit code the launcher reads (`0` vs `3`). Also restore correctly when the server dies underneath the client, which is the likeliest crash path.
- `KeyEventKind::Release` dropped; `Repeat` forwarded once per repeat; non-empty modifiers dropped (this is the `Ctrl-d` → `deleteCursorQuantum` trap).

## Done when

- The terminal is left usable after: `ZQ`/`ZD`, `kill -9` of the client's server, and a panic in the client. No stuck raw mode, no leftover alternate screen, no eaten shell.
- Resizing the window mid-edit stays correct and does not leak stale rows.
- On a terminal without `?2026` the client still works with no visible artifacts beyond tearing.

## Result

Two modules and one bug fix. `caps.rs` asks the terminal what it can do; `lifecycle.rs` owns the ordered restore; the
loop now treats a hung-up socket as the end of the session.

### The bug the ticket existed for

`take_view` returned `None` for both `Incoming::Idle` and `Incoming::Closed`, so a client whose server died spun at full
CPU forever, never restoring the terminal — the terminal stayed in raw mode on the alternate screen until it was killed
from outside. `Closed` is now `Taken::Gone`, which ends the run with exit code **4**. Measured by the pty test that hangs
up on the client: before, it never exited; now it exits in well under 3 s with the restore sequence in the right order.

### The rest

- **`caps.rs`** — `PROBE` (`CSI ? 2026 $ p`), `PUSH`/`POP` (kitty `DISAMBIGUATE_ESCAPE_CODES`), and `sync_output()` which
  turns the reply into supported / unsupported / unknown (`1` set, `2` reset, anything else is no answer). Frames are
  wrapped in `?2026h`/`?2026l` only when the answer said supported; the push is unconditional because an unparsed CSI is
  ignored where it is unknown.
- **`lifecycle.rs`** — `Restore` guard, idempotent, assembled from `caps::POP` so the pop cannot drift from the push.
  `Drop` covers the panic path (a `panic::set_hook` is not needed and would compete with the default hook's output);
  `now()` is spent by hand before `process::exit`, which skips destructors.
- **`SIGWINCH`** — crossterm already reports it as `Event::Resize`; the loop re-declares `viewportWasChanged {cols, rows}`
  with content rows and drops the cached frame, so the server's `full: true` answer is what gets painted.

### Two findings the probe forced (recorded in spec §5.5)

1. **The answer cannot come through crossterm.** A `DECRQM` reply is not a key event, so the client reads fd 0 itself,
   before its first `poll`, with a 60 ms budget. Cost: bytes typed inside that window are consumed with the answer.
2. **That window is blind to resizes.** crossterm installs its `SIGWINCH` handler on the first `poll`, so a resize during
   the probe is a signal nobody receives. Caught by the resize test reproducing it 100% of the time; fixed by re-reading
   the terminal size after the probe and declaring again if it moved. The assertion that caught it is now about the
   *geometry of the frame painted afterwards*, not about "a repaint happened" — the client was right and the test was
   wrong the first time.

### Verified

`36 unit tests, 5 pty checks` — the pty ones need no JVM:

| Check | Asserts |
|---|---|
| server hangs up | exits within 3 s, non-zero and neither `0` nor `3`, cursor restored before the alternate screen is left, nothing painted after |
| resize to 120×40 | a fresh `viewportWasChanged {cols: 120, rows: 39}` reaches the server, the frame painted afterwards addresses 40 rows, cursor inside the new grid |
| probe unanswered | `?2026$p` is sent, `?2026h` is never used, the kitty push is sent at attach and popped before `?1049l` |
| probe answered "set" | frames are wrapped in `?2026h`/`?2026l` |
| real server (`VISUR_E2E=1`) | painting, editing, search, `ZQ` — 5.96 ms median keystroke → paint, unchanged by this ticket |

`ZD`, `kill -9` of the server and a client panic are covered as above (`ZD` shares `ZQ`'s restore path; the panic is
proven at the guard seam, not end-to-end).

### Not done

- **Paste** (`Event::Paste`) is unimplemented, so "disable paste" is absent from the restore rather than skipped — it
  waits for P3.
- **A client panic is not exercised end-to-end**: proving `Drop` runs while unwinding is a unit test with a writer; a
  production switch to force a panic was judged worse than the gap.
- **`?2026` on a real terminal is unverified by machine.** The pty harness answers both ways; Ghostty/iTerm2 answering
  "set" for real is a thing to eyeball once.
