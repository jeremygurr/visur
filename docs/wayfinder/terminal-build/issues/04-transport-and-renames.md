# `/ws` transport, the inbound rename, and deleting the debug loop

Type: task
Status: open
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
