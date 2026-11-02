# Terminal client: capabilities, lifecycle, and quitting

Type: task
Status: open
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
