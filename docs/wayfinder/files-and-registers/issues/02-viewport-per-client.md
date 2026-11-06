# Viewport per client

Type: task
Status: open
Blocked by: —

## Work

`canvasWidth` and `canvasHeight` live in the shared editor model (`EditorContentService.java:56,104`), and
`viewportWasChanged` writes them from whichever client last resized. So **this is a live bug, not a future one**: open
the browser page next to the terminal client and resize the browser, and the terminal's line wrapping, cursor column and
scroll window change under it. `RelativeLineBoundCalculator` reads `emc.getCanvasWidth()` for exactly that work.

This is the smallest possible first slice of per-client state: one concept, two fields, and an existing bug to pin. It is
deliberately **before** [03](03-document-session-split.md), because the split is 57 classes and this is a way to discover
what "which client" means while it is still cheap to answer.

**What has to happen.** A connection needs an identity: `/ws` accepts a socket and `MainVerticle.java:95-97` gives each
one a consumer that writes `viewWasChanged` straight out. `viewportWasChanged` arrives on that same socket with no id
attached, so the client has to say who it is, or the socket has to be the thing that remembers. Prefer the socket: bind
the viewport to the connection, not to a client-supplied id, so a second browser tab cannot claim the first one's
geometry.

**The view has to be rendered per client.** `ModelWasChangedVerticle` publishes one payload today; if viewports differ,
one render per connection is the only honest answer, since `rows[{start,text}]`, `cursorRow`, `cursorCol` and the clip of
the mode line all depend on width. Rendering N times for N clients is fine at N=2 and N=3; say so rather than optimising.

**Something has to answer for a server started with no client attached.** The boot path currently renders at whatever
the default is; keep a default viewport and use it until a connection states otherwise, so `visur` headless and the pty
tests keep working.

## Done when

- Two connections with different column counts render different `rows` and different cursor columns, simultaneously.
  This is the test that proves the ticket, and it is a test at the wire seam, not of `EditorModelCoupler` in isolation.
- A resize on one client leaves the other's rendering byte-identical. `ViewportWasChangedVerticle` is the seam.
- A disconnect forgets that viewport. Nothing accumulates per connection forever.
- The pty tests still pass, and the browser page still renders correctly when it is the only client (including after the
  window is resized, which is the case that currently works by accident).
- A third client attaching mid-session does not change what the first two see.

## Notes

- The Rust client sends `viewportWasChanged` from `transport.rs:52-54` and the browser from `visur.js:49-53`; both send
  cols and rows already, so no client protocol work beyond identity.
- **Do not solve this by moving the fields into a `Session` class and stopping.** A `Session` that is still reached
  through a singleton getter is the same bug with a nicer name. The question this ticket has to answer out loud is *how
  does the code currently executing know which connection it is executing for*, and the operator dispatch path
  (`KeyWasPressedVerticle.press` → `ces.execute` → operators reaching `ServiceHolder.editorModelCoupler`) is where that
  answer lives.
- That is also why this ticket exists separately from [03](03-document-session-split.md): the answer is the same for the
  viewport and for the cursor, and it is cheaper to find once with two fields at stake than with fifty.
- The scroll window (`fy`, `canvasStart`) and `virtualCX` are already per-view in spirit — they are derived from the
  viewport — and they are the first thing that will turn out to be per-session rather than per-document. Note the finding
  in the split ticket either way.
