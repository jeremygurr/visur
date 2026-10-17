---
type: Concepts
title: frontend-overview
description: Browser-side JavaScript rendering and communication with the Vert.x server
tags: [frontend, javascript, canvas, websocket]
sources:
  - resource: src/main/resources/static/visur.html
  - resource: src/main/resources/static/visur.js
  - resource: frontEnd/editingScreen.html
  - resource: frontEnd/editingScreen.js
  - resource: src/main/resources/static/browser_input_service-proxy.js
generated: { by: visur-wiki-updater/pi, at: 2026-10-13T17:00:00Z }
updated: { by: visur-wiki-updater/pi, at: 2026-10-13T17:00:00Z }
---

# Frontend Overview

## Description

Visur's frontend consists of two HTML/JavaScript implementations:

**Main Frontend (static/visur.html + static/visur.js):** The production frontend that communicates with the Vert.x server via WebSocket (sockJS) and the Vert.x EventBus.[^1] Key features:

- Canvas-based rendering with dynamic sizing (window.innerWidth × window.innerHeight)
- Cell-based coordinate system (cellWidth = textWidth + 1, cellHeight = 20)
- Event bus connection at `http://localhost:8888/eventbus`
- Listens on `viewWasChanged` address for state updates[^2]
- Sends `keyWasPressed` events for keyboard input[^3]
- Sends `canvasWasChanged` for viewport sizing
- Renders text using 20px Courier font on 2D canvas[^4]
- Displays cursor as yellow rectangle (span > 0) or vertical bar (span = 0)
- Shows current mode/submode in footer status bars
- Updates quantum display (e.g., "CHARACTER QUANTUM")

**Development Frontend (frontEnd/editingScreen.html + frontEnd/editingScreen.js):** A simpler, self-contained prototype that doesn't connect to the server. Uses a hardcoded example paragraph, h/j/k/l cursor movement, insert mode (i/Esc), and Backspace.[^5][^6]

## Key Takeaways

- Canvas-based 2D rendering with cell coordinates
- WebSocket communication via sockJS + Vert.x EventBus
- 4 event bus addresses: viewWasChanged, keyWasPressed, canvasWasChanged, modelWasChanged
- Two frontends: full production and simple prototype

## Appearances in Sources

- [[visur.html](../../src/main/resources/static/visur.html)] — production HTML page
- [[visur.js](../../src/main/resources/static/visur.js)] — production JavaScript rendering logic
- [[editingScreen.html](../../frontEnd/editingScreen.html)] — prototype HTML page
- [[editingScreen.js](../../frontEnd/editingScreen.js)] — prototype JavaScript
- [[browser_input_service-proxy.js](../../src/main/resources/static/browser_input_service-proxy.js)] — generated key-press service proxy (unused)

## Related Concepts

- [[main-verticle](main-verticle.md)] — server configures event bus bridge for frontend
- [[canvas-cell](canvas-cell.md)] — canvas cell rendering
- [[visualization-color](visualization-color.md)] — canvas colors
- [[browser-input-service-proxy](browser-input-service-proxy.md)] — the generated `BrowserInputService` proxy

## Related Pages

[[view](view.md)]
- [[main-verticle](main-verticle.md)]

## References

[^1]: [[visur.js](../../src/main/resources/static/visur.js#L1-50)] — EventBus connection, canvas setup, state variables
[^2]: [[visur.js](../../src/main/resources/static/visur.js#L51-100)] — viewWasChanged handler extracting full editor state
[^3]: [[visur.js](../../src/main/resources/static/visur.js#L101-150)] — keyboard handling via sendKeyPressedEvent()
[^4]: [[visur.js](../../src/main/resources/static/visur.js#L151-200)] — drawCanvas() rendering text and cursor on 2D canvas
[^5]: [[editingScreen.js](../../frontEnd/editingScreen.js#L1-50)] — prototype canvas setup with h/j/k/l navigation
[^6]: [[editingScreen.js](../../frontEnd/editingScreen.js#L51-100)] — insert mode with i/Esc, Backspace handling
