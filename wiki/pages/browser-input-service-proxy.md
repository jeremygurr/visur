---
type: Sources
title: BrowserInputService Proxy
description: Generated Vert.x service proxy that pushes browser key presses onto the event bus
resource: src/main/resources/static/browser_input_service-proxy.js
tags: [frontend, javascript, event-bus]
generated: { by: visur-wiki-updater/pi, at: 2026-10-13T22:05:00Z }
updated: { by: visur-wiki-updater/pi, at: 2026-10-13T22:05:00Z }
---

# BrowserInputService Proxy

**Source:** [[browser_input_service-proxy.js](../../src/main/resources/static/browser_input_service-proxy.js)]
**Date updated:** 2026-10-13

## Summary

`browser_input_service-proxy.js` is a Vert.x service-proxy file (Apache-2.0, Red Hat 2014 header) that defines a `BrowserInputService` browser-side proxy.[^1] Its `keyPress(key)` method sends `{"key": <key>}` with the metadata `{"action":"keyPress"}` to an event-bus address via `EventBus.send` — the browser→server path for keyboard input.[^2] The module also exposes `createProxy(vertx, address)` and `create(vertx)` factories.

The file is not loaded by the served frontend: `visur.html` pulls in sockJS, the Vert.x EventBus bridge client, and `visur.js` only, and no Java `BrowserInputService` service exists in the repository. Its `/// <reference>` comment points at a `.d.ts` file that is also absent, and `createProxy`/`create` reference constructor-scoped variables, so the proxy is a generated, currently unused artifact.

## Key Takeaways

- Vert.x-generated JavaScript service proxy, not hand-written
- `keyPress(key)` sends key events to an event-bus address with `action: keyPress`
- Not referenced by `visur.html`, and no matching Java service exists — currently unused
- Its `.d.ts` type reference is missing from the repo

## Entities & Concepts

- [[key-was-pressed-verticle](key-was-pressed-verticle.md)] — the server verticle that handles key presses
- [[bus-event](bus-event.md)] — the event bus addresses it would send to
- [[frontend-overview](frontend-overview.md)] — the production frontend that sends keys via `visur.js` instead

## Relation to Other Wiki Pages

- [[frontend-overview](frontend-overview.md)] — lists this file among the frontend sources
- [[main-verticle](main-verticle.md)] — mounts the event bus bridge the proxy would use

## References

[^1]: [[browser_input_service-proxy.js](../../src/main/resources/static/browser_input_service-proxy.js#L17-35)] — Apache header, module wrapper, and the `BrowserInputService` constructor
[^2]: [[browser_input_service-proxy.js](../../src/main/resources/static/browser_input_service-proxy.js#L54-66)] — `keyPress` sends the key over the event bus
