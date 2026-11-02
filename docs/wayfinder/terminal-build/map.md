# Map: terminal-build

## Destination

Visur's terminal client is a **daily driver for the current feature set**: it paints and edits the live document at approximately vim latency, and the view protocol is viewport-bounded so the canvas client stops paying for the document.

Reaching the destination means: the protocol in [the spec](../renderer-delivery/spec.md) is implemented, a terminal client built to that spec runs against the real server, and both renderers are measured against the spec's budgets at 100 KB / 1 MB / 5 MB.

**One client at a time.** Simultaneous attach is deliberately not in this effort.

## Notes

- **This map carries execution.** Unlike the predecessor map, tickets here are build slices, not decisions. The reason: every design question was already answered in [renderer-delivery](../renderer-delivery/map.md), and what remains is work. Ticket shape is therefore `## Work` / `## Done when` / `## Notes` rather than `## Question`.
- **The spec is the source of truth**: [renderer-delivery/spec.md](../renderer-delivery/spec.md). If reality contradicts it, the contradiction is a finding to record, not a decision to make silently.
- **The reference implementation is [the spike](../renderer-delivery/spike/README.md)** — 374 naive lines that already paint, edit and quit against the real server at 2.6–4.4 ms. Read it before writing the client; it encodes the `/ws` envelope, the key adapter's modifier rule, and a naive full repaint to beat.
- **Deliberately out: session/per-subscriber state (P1).** With one client attached, the singleton viewport is correct. Two clients still fight over it — a known limitation, documented in the spec, not fixed here.
- **Deferred to a multi-user effort, with its first question unsettled**: the charting conversation gave two different answers. Q3 accepted "one document, N viewports — one shared cursor", while the stated long-term goal is "independent views of the same document with independent cursors they can control". Those are different designs and the later effort must choose; this map should not preclude either, which is another reason not to build session state now.
- **Measurement is a ticket, not an afterthought.** The spec's budget (≤16 ms keystroke → paint at 5 MB) is untestable today: nothing can load a 5 MB document, because there is no file I/O.
- **Skills every working session should consult**: `tdd` (the client and `Viewport` are both testable), `diagnosing-bugs` when a measurement contradicts the spec, `codebase-design` when a slice starts fighting its seams.

## Decisions so far

<!-- one line per closed ticket: gist, then link to the ticket holding the detail -->

- [Terminal client skeleton](issues/05-client-skeleton.md): the client exists — `terminal/`, 28 unit tests at four pure seams plus a Rust pty test that types keys and reads the screen back, so no second language is needed to verify it. **3.45–3.74 ms** median keystroke → paint (5.7 ms against a just-started server, which is JVM warm-up). Found a spec bug (`SHIFT` arrives set on uppercase, so dropping modifiers made `Z` and every uppercase binding unreachable) and a server bug (`viewWasChanged` was `send`-ed, so a second renderer silently stole half the views; now published).

- [Make large documents loadable](issues/01-benchmark-content.md): `VISUR_CONTENT_BYTES` + `SizedContent`; verified at 6 KB, 1 MB and 5 MB with the browser rendering each.
- [`/ws`, the inbound rename, and the startup race](issues/04-transport-and-renames.md): one name per direction, content-cell viewports, a loopback-only plain JSON socket, and the port now opens only after the verticles deploy — which deleted the declaration-resend ritual every client had been carrying. Spike: 4.3 ms median with no SockJS.
- [Switch the wire to viewport rows](issues/03-switch-payload.md): payload flat at ~5 KB from 6 KB to 5 MB (1,038× smaller at 5 MB), key→paint flat at 32 ms, 44 `fillText` per frame instead of 3,558. The browser's `modelWasChanged` echo — which was silently stealing half of all views — is gone.
- [Extract Viewport and View](issues/02-extract-viewport-view.md): `Viewport` lays out and maps the cursor, `View` projects and serialises, the verticle is a pipe. The payload gained rows but kept the three fields the browser still paints from; §3.1's wrap-alignment invariant turned out not to be needed.

## Not yet specified

Fog — in scope, real, but not sharp enough to ticket yet.

- **How the terminal exposes page-scroll.** The spec makes scrolling server-mediated and follow-the-cursor; whether beta needs overscan, a scroll message, or nothing at all shows up when the client is real enough to try.
- **What "approximately vim latency" costs in practice.** The spike hit 2.6–4.4 ms with a naive renderer; the real client adds diffing and capability probing. If it lands near 2 ms the budget is met; if diffing costs more than it saves at 30–60 rows, the conformance ticket may remove it — worth watching rather than assuming.

## Out of scope

Work consciously ruled out of this effort. Never graduates; returns only if the destination is redrawn.

- **Simultaneous attach / multi-user editing.** Next effort, and its first question is the shared-cursor-vs-independent-cursors conflict above.
- **Session-scoped viewport state (P1)** and any per-subscriber delivery — only needed by the above.
- **Every Visur feature gap** — no quit, span clamped to 0/1, no copy/paste, no document-jump keys, no search-and-replace, no file I/O. These are the multi-user/feature effort's workload and its benchmark set.
- **Filesystem navigation, typed objects, remote attach, TLS/auth, a GUI client, mouse/panes/images in the terminal, any plugin system.**
