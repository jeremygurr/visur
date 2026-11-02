# `/ws` transport, the inbound rename, and deleting the debug loop

Type: task
Status: closed
Closed: 2026-11-01
Blocked by: 03

## Work

Spec §2 and §7 step 3.

- **Rename the inbound address** `canvasWasChanged` → `viewportWasChanged`, body `{cols, rows}` in cells. Update [`BusEvent`](../../../../src/main/java/com/ple/visur/BusEvent.java), the inbound permit in [`MainVerticle`](../../../../src/main/java/com/ple/visur/MainVerticle.java), `visur.js`, and the spike if it is still in use. **The outbound stays `viewWasChanged`** — one address per direction, or a bridge echoes each client's size message to every client.
- **Rename `CanvasWasChangedVerticle` → `ViewportWasChangedVerticle`**, body becomes `putViewport` → `modelWasChanged`, and **delete the debug loop at lines 25-33** that prints every line of the document on every resize.
- **Add the `/ws` route** via `HttpServerRequest.toWebSocket(...)`: on message, dispatch `viewportWasChanged` / `keyWasPressed` onto the event bus; on `viewWasChanged`, write it to the socket; on close, unregister. Localhost only, no auth.
- The SockJS bridge stays for the browser. Both transports coexist.

## Done when

- The browser still works end to end through `/eventbus`.
- A throwaway script can connect to `/ws`, register, send a key, and receive a `viewWasChanged` with the new payload — and no SockJS framing or heartbeat appears anywhere in that script.
- No per-document logging remains on the resize path.

## Result

All three parts, plus the startup race that every client until now had been working around.

### The rename, and what "rows" turned out to mean

`viewportWasChanged` replaces `canvasWasChanged`; the body is `{cols, rows}` in cells. The outbound stays
`viewWasChanged` — one name per direction.

The rename exposed a **double subtraction of the chrome row**. Spec §2 says the declared `rows` are content rows
that exclude client chrome, but [`View`](../../../../src/main/java/com/ple/visur/View.java) *also* subtracted
`LAST_CANVAS_ROW_IS_NOT_CONTENT` from whatever it received, and
[`EditorModelCoupler.getCanvasEnd`](../../../../src/main/java/com/ple/visur/EditorModelCoupler.java) subtracted it a
third time. The browser was sending a chrome-inclusive height to cancel that out. Both subtractions are gone and
the browser now sends content rows, so `getCanvasHeight()` means one thing everywhere: content rows. The invariant
is enforced at both ends — the harness rejects more rows than were declared, and rejects a cursor outside a full
viewport.

### The echo of the bus, once more, in the other direction

`keyWasPressed` and `viewportWasChanged` used to carry a JSON **string** body. Spec §2 gives the viewport body as
an object, and a `/ws` client would naturally send one, so the two transports now carry object bodies and the two
verticles decode with one helper — `jsonBody` — that accepts an object (from `/ws`) or a string (from the SockJS
bridge). That is the transport edge, not a format sniff: the message is the same in both cases.

### `/ws`

`HttpServerRequest.toWebSocket`, loopback only (403 otherwise — it is a direct line to the editor's event bus), and
frames are the bus envelope in both directions, documented in spec §2.1. One consumer per socket; with one client
attached that is unambiguous. The SockJS bridge stays for the browser.

### The startup race, fixed at the source

`deployVerticle` is asynchronous and `listen(8888)` was not chained to it, so the port opened while the bus
consumers might not exist — and `send` to an address with no consumer is dropped **silently**. That is the mechanism
behind the spike's recorded finding that "the first declaration is regularly lost", and behind every client
re-sending its declaration on a timer. The deploys are now chained, so the port opens only after the three
verticles are deployed.

Honest scope of that claim: the mechanism was identified by reading the deploy code, not by reproducing a lost
declaration. What is now verified is the guarantee's *effect* — a single declaration always produces a view, across
the browser harness, the `/ws` probe and the spike. The failure mode for a client that connects too early changed
from "blank screen, forever" to "connection refused", which is visible and retryable.

### Verified

- **Browser, through `/eventbus`**: PASS at 112x45, 68x29 and 147x53 content cells — rows match the declared count
  exactly (44/29/53), cursor inside its row, a full viewport never leaves the cursor outside it, the view answering
  the declaration is `full: true`, keystrokes are not, selection gold pixels match, search submode reaches the mode
  line, 227k pixels painted.
- **`/ws` probe** (`.scratch/ws-probe.cjs`, no dependencies, no SockJS): declares `100x29`, receives 29 rows in a
  3,451-byte payload with exactly the seventeen §1.2 fields, sends `{"address":"keyWasPressed","body":{"key":"j"}}`
  and receives a view with the cursor moved. Asserts every frame is plain JSON and never an `a[...]` array.
- **Spike**, in a pty: text, mode line, cursor; **median 4.3 ms** over 15 keystrokes (was 6.2 ms on SockJS), p95 7.0.
  Its SockJS framing, 5 s ping and 300 ms resend loop are all deleted, and it declares once.
- **No per-document logging on the resize path**: 0 lines in the server log of a run that resized, where a single
  resize used to print the cursor's line 56 times.
