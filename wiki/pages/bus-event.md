---
type: Sources
title: BusEvent
description: Event types used in the Vert.x event bus communication between browser and server
resource: src/main/java/com/ple/visur/BusEvent.java
tags: [event-bus, communication, protocol]
generated: { by: visur-wiki-updater/pi, at: 2026-10-13T17:00:00Z }
updated: { by: visur-wiki-updater/pi, at: 2026-10-13T17:00:00Z }
---

# BusEvent

**Source:** [[BusEvent.java](../../src/main/java/com/ple/visur/BusEvent.java)]
**Date updated:** 2026-10-13

## Summary

BusEvent defines the event types used for communication over the Vert.x event bus between the browser client and the server.[^1] The main event addresses are:

- `keyWasPressed` — Browser → Server: Keyboard events (key name)
- `modelWasChanged` — Server → Server: Internal notification of model changes (triggers ModelWasChangedVerticle)
- `canvasWasChanged` — Browser → Server: Canvas resize/viewport updates
- `viewWasChanged` — Server → Browser: View state updates (sent to clients with full editor state)

## Key Takeaways

- 4 event addresses for browser↔server communication
- keyWasPressed and canvasWasChanged are client→server
- modelWasChanged and viewWasChanged are server→client/broadcast

## Entities & Concepts

- [[main-verticle](main-verticle.md)] — configures event bus bridge with these addresses

## References

[^1]: [[BusEvent.java](../../src/main/java/com/ple/visur/BusEvent.java#L1-16)] — defines event bus addresses and message formats
