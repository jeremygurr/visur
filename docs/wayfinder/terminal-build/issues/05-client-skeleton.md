# Terminal client skeleton: connect, paint rows, take keys

Type: task
Status: closed
Closed: 2026-11-01
Blocked by: 04

> [!note] The spike already speaks the new protocol
> [Ticket 03](03-switch-payload.md) moved it to `rows`, so it is a working reference for this ticket rather than
> something to repair first. What it does *not* have is any of this ticket's real work: damage tracking, the
> ordered terminal restore, `SIGWINCH`, or a launcher.

## Work

Spec §5.2–5.4, built to the component boundaries in [the design](../../renderer-delivery/design/terminal-client-components.md). Start from [the spike](../../renderer-delivery/spike/visur-term/src/main.rs) — it already solves the hard, uninteresting parts (raw mode, layout, cursor, key adapter) and predates `rows`, so its job is to be read and then mostly deleted.

Minimum: connect to `/ws`, decode the payload, paint `rows` (full repaint, no diffing yet), draw a mode line, and send key tokens for `h j k l`, arrows, `i`, `a`, `o`, `Esc`, printable characters, `Backspace`, `Enter`, `s`+digit, and search. Raw mode, alternate screen, and *no* SockJS.

Follow-the-cursor scrolling only: the renderer holds its viewport's rows and cannot scroll past them (spec §5.8).

## Done when

- Every key in the spec's **must** list except the terminal-lifecycle ones works against the real server, verified by driving it headlessly in a pty (the Rust pty test does this — `terminal/tests/pty.rs`).
- Typing feels immediate to a human; any perceptible lag is recorded with a number rather than a shrug.
- The client contains no wrapping arithmetic and no SockJS framing.

## Result

The client exists at [`terminal/`](../../../../terminal/README.md) — `visur-term`, six modules and 29 tests, of which
28 are unit tests at four pure seams and one is the end-to-end pty check.

| Module | Component | Tested at the seam by |
|---|---|---|
| `keys.rs` | 2, key adapter | the §5.4 token table as literals |
| `zseq.rs` | 7, quit | the `Z` prefix as a state machine |
| `protocol.rs` | 10, codec | a hand-written 17-field payload literal |
| `screen.rs` | 4, screen model | grid placement, span clipping, mode line, clamping |
| `paint.rs` | 6, emitter | exact escape bytes for a small grid |
| `transport.rs`, `main.rs` | 9, 8, 7 | the pty test, end to end |

### Latency

**3.45–3.74 ms** median over 10 keystrokes per run, three consecutive runs against a warm server (debug build
3.57/3.63/3.74, `--release` 3.45). A run against a server that has just started measures **5.7 ms**, which is JVM
warm-up rather than client work. That is what the harness measures from writing the key to the pty until the frame
terminating that key arrives, so it includes the pty round trip, the server and one frame of paint — the spike
measured 2.6–4.4 ms on the same basis. Not perceptible at a keyboard; still above the spec's ~1–2 ms bar, and the
remaining cost is the loop's 500 µs read timeout plus a full 100×30 repaint per key. Ticket 06 (damage) and ticket
07 (a real `poll` over both fds) are where that shrinks.

### Two real bugs, one of them in the spec

1. **`SHIFT` arrives set on uppercase characters.** The spec said `SHIFT` "needs no special handling: it already
   appears as the uppercase `Char`", and the adapter therefore dropped every non-empty modifier set. On a real
   terminal `Z` arrives as `Char('Z')` **with `SHIFT`**, so the adapter dropped it — `ZQ` did nothing, the client
   could not be quit, and the first symptom was a pty test that hung. Correction: drop *chords*
   (`CONTROL`/`ALT`/`SUPER`/`HYPER`), keep `SHIFT`. Spec §5.4 and the component design are updated.
2. **`viewWasChanged` was `send`-ed, not `publish`-ed.** With one renderer this cost nothing. Attaching a second
   consumer — the terminal — exposed it: `send` picks exactly one consumer per message, so a browser and a terminal
   each saw roughly half the views, and a socket that died without unregistering swallowed them silently. That was
   the intermittent blank screen: a client's first declaration would produce a view delivered to somebody else.
   The server now publishes ([spec §2](../../renderer-delivery/spec.md)).

### Also learned

- **`Backspace` is bound in insert and replace only, not navigate** — like vim. Not a gap; the test was wrong to
  expect deletion after leaving insert.
- **No test can assume the document is at its seed.** The server's document is shared, so the harness asserts on
  shape and on a marker it types itself. Same lesson the browser harness learned at ticket 03.
- **A frame boundary must be respected when reading a pty.** The first version of the harness read whatever bytes
  were present, which meant it once asserted against a half-written frame.
- **A Rust module that is not declared in `main.rs` is invisible to `cargo test`** — the first red was a false pass
  because `mod zseq;` was missing and the suite quietly tested one module less.

### Deliberately still missing

Follow-the-cursor scrolling is *absent, not implemented*: `rows` is the viewport, so scrolling is the server's, and
the client has no arithmetic to remove (done-when satisfied by having none). `SIGWINCH`, the `?2026` probe, and the
ordered restore belong to ticket 07; damage tracking to ticket 06; the launcher and the meaning of the `ZQ`/`ZD`
exit codes to ticket 08.
