# Viewport per client

Type: task
Status: closed
Closed: 2026-11-06
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
  **Done:** `ViewportPerClientTest.twoClientsDeclareDifferentWidthsAndEachSeesItsOwn`, two real sockets on `/ws`.
- A resize on one client leaves the other's rendering byte-identical. `ViewportWasChangedVerticle` is the seam.
  **Done:** `aResizeOnOneClientLeavesTheOtherLookingByteIdentical` compares whole rendered payloads.
- A disconnect forgets that viewport. Nothing accumulates per connection forever. **Done:**
  `aDisconnectTakesTheViewportWithIt`, and the close *and* exception paths both forget.
- The pty tests still pass, and the browser page still renders correctly when it is the only client (including after the
  window is resized, which is the case that currently works by accident). **Done for the tests** (49 + 10 unchanged, and
  the e2e ones pass against a seeded server — see the finding about those). **Not done by a human for the page.** Note
  that the page has still never re-declared on resize: it declares once at load, so a resized browser is drawn at the
  width it loaded at. That is the accident, and this ticket did not change it.
- A third client attaching mid-session does not change what the first two see. **Done:**
  `aThirdClientJoiningChangesNothingAboutWhatTheFirstTwoAreShown`.
- One more, added on the way: a client that never declares a grid is still given one, so a headless server answers a
  keypress instead of throwing. See the result.

## Result

`canvasWidth` and `canvasHeight` are gone from the editor model: the enum has no such keys and
`EditorContentService` has no such methods. [`Connections`](../../../src/main/java/com/ple/visur/Connections.java) is
the only place a grid is kept — id → grid, plus open/close — and `EditorModelCoupler.getCanvasWidth` answers for the
connection currently in play. `emc.putCanvasWidth` survives, rescaling the connection in play, which is why none of the
fifteen test classes that set up a window had to change.

**How the code knows which connection it is executing for: the socket says so, and the handler adopts it.**
`MainVerticle.webSocket` mints an id (`ws-1`, `ws-2`, …) when it accepts a socket, stamps that id as a bus *header* on
every frame it relays, and forwards back only the views stamped with it. So `viewportWasChanged` needs no id in its
body — the client that resized is the socket that said so, which is the thing the Work section asked for: a client
cannot name itself, so it cannot claim another window's geometry. `KeyWasPressedVerticle.handle`, `handlePaste`,
`ViewportWasChangedVerticle.handle` and the render loop each begin with `emc.putConnection(headerOf(event))`. Operators
are untouched: `CharacterQuantum` and `RelativeLineBoundCalculator` still call `emc.getCanvasWidth()` and now get the
asking client's width, which is what they always meant.

**That is dynamic scope, and it is not the trap the Notes warned about.** The ambient thing is an id, not a value, and
there is no "the viewport" left to reach through a singleton getter — `Connections.viewport(id)` has no argument-less
sibling. It is safe because a command never yields: every verticle is deployed once, so its consumers are serialised on
one context, and adopt-then-run cannot interleave with another client's adopt. Ticket
[03](03-document-session-split.md) inherits the pattern, which is the point of finding it here with two fields at stake.

**The browser did not need an identity mechanism; it needed the transport the terminal already had.** The Work section
preferred the socket over a client-supplied id, and the SockJS bridge at `/eventbus` is exactly the transport that
cannot carry a socket's identity: it hands the bridge a JSON body and nothing else. So the page was moved to `/ws`
speaking the same `{"address": …, "body": …}` frames as the terminal client, and the bridge was deleted. Three things
came free: the page no longer loads two scripts from unpkg (it was broken offline, and hardcoded `localhost:8888` on
top of that), every client's id is now server-minted, and the server has one way in instead of two — with the loopback
check that `/ws` already had, which closes the hole ticket [08](08-clipboard-transport.md) was about to widen.

**The default grid is 80×24, and it fixed a crash this ticket did not set out to fix.** A server with no client
attached used to answer its first `modelWasChanged` with a `NullPointerException` in `getCanvasHeight` — observed
before this ticket existed, while driving a server by hand. `Connections.viewport` answers the default for a connection
that has not declared, so headless boot and the pty tests have a grid.

**Two findings about testing at this seam, both cheap to repeat.** First: a view is now rendered per connection, so a
wire test cannot read "the next frame" — every change repaints every client and a socket's queue holds views of states
that have already moved on. The read drains the queue after it goes quiet, then re-declares its own grid to force a
repaint; a viewport declaration is the only wire event that repaints without changing the model. Second: the first
version of the test failed with *connection refused* rather than the assertion it was written to fail on, because
`MainVerticle` ignored its own bind failure and connected to whatever was already on 8888 — the swallowed `listen()`
result ticket [07](07-launch-argument.md) now names. The port reads a `visur.port` system property before the
environment variable so a test can bind its own.

**Finding: three pty e2e tests cannot all pass against one shared server, and one of them fails on `HEAD`.** With
`VISUR_E2E=1`: `a_resize_during_a_search_submode…` and `the_client_paints…` want a document of more than 500 non-space
characters, `live_multiline_paste_and_document_jumps` pastes into the document and then yanks from *document start*, so
it wants the paste to be the beginning of the document. They share one server, so one seed cannot serve both — and with
a seeded server (`VISUR_CONTENT_BYTES=4000`) `live_multiline_paste_and_document_jumps` fails identically with and
without this ticket's code, which is the control run that proves it is not a regression. Two more things worth knowing:
a server started with neither `VISUR_FILE` nor `VISUR_CONTENT_BYTES` has an *empty* document (the Declaration text in
`InitializerService` is not what boots), and the e2e tests' precondition message says only "start one with
`./gradlew run`", which is how you get the empty one. Whichever ticket next touches those tests should give each its own
server or its own document; it is not this ticket's cleanup.

**Numbers:** Java 217 tests (from 212: five in `ViewportPerClientTest`), Rust 49 + 10, unchanged. Mutation checks: delete
the socket's header filter and four of the five go red; delete `Connections.close` from the close handler and the
disconnect test goes red; change the default grid and the never-declared test goes red.

**Not checked: the browser page, by a human.** Its transport changed, so the check is: open
`http://localhost:8888/`, type, and confirm it paints — then open a second tab and confirm neither tab's window size
moves the other's text. The page's own rendering is unchanged code; only what carries the view to it is new.

## Notes

- **What ticket 03 inherits:** the scroll window (`fy`, `canvasStart`) and `virtualCX` are read through the same
  `getCanvasWidth` calls this ticket rerouted, and they are *editor state* written during a command — so the moment they
  are per-client, so is whatever writes them. They are the first thing the split has to decide about, and the adopt at
  dispatch is already in place for them.
- The Rust client sends `viewportWasChanged` from `transport.rs` and the browser from `visur.js`; both sent cols and
  rows already, so no client protocol work was needed at all. The protocol did not change in this ticket; the transport
  count went from two to one.
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
