---
type: Sources
title: MainVerticle
description: Application entry point that starts the Vert.x event bus and deploys all verticles
resource: src/main/java/com/ple/visur/MainVerticle.java
tags: [entrypoint, verticle, startup]
generated: { by: visur-wiki-updater/pi, at: 2026-10-13T17:00:00Z }
updated: { by: visur-wiki-updater/pi, at: 2026-10-13T17:00:00Z }
---

# MainVerticle

**Source:** [[MainVerticle.java](../../src/main/java/com/ple/visur/MainVerticle.java)]
**Date updated:** 2026-10-13

## Summary

MainVerticle is the application entry point for Visur, a Vim-inspired rich text editor built with Vert.x. It initializes the Vert.x event bus, deploys all server-side verticles (including ModelWasChangedVerticle, CanvasWasChangedVerticle, KeyWasPressedVerticle), and starts the HTTP server on port 8888.[^1] The verticle configures the event bus bridge to allow browser clients to communicate with the server through addresses like `keyWasPressed`, `modelWasChanged`, and `canvasWasChanged`.

The main flow is: (1) create a Vertx instance, (2) deploy ModelWasChangedVerticle (handles text model updates), (3) deploy CanvasWasChangedVerticle (handles viewport rendering), (4) deploy KeyWasPressedVerticle (handles keyboard input), (5) deploy the HTTP server serving the HTML frontend, and (6) configure the Stomp event bus bridge for WebSocket communication.[^2]

## Key Takeaways

- MainVerticle is the single deployment entry point
- Deploys 3 core verticles for the editor's event loop
- Serves the HTML frontend and configures WebSocket/EventBus bridge
- Listens on port 8888 for browser connections

## Entities & Concepts

- [[model-was-changed-verticle](model-was-changed-verticle.md)] — handles text model updates
- [[canvas-was-changed-verticle](canvas-was-changed-verticle.md)] — handles canvas rendering
- [[key-was-pressed-verticle](key-was-pressed-verticle.md)] — handles keyboard input
- [[editor-model-coupler](editor-model-coupler.md)] — shared state management
- [[logging-configuration](logging-configuration.md)] — Logback logging configuration for the server

## Relation to Other Wiki Pages

MainVerticle connects to all three core verticles and is the starting point for the entire application lifecycle. See [[editor-model-coupler](editor-model-coupler.md)] for how all verticles share editor state.

## References

[^1]: [[MainVerticle.java](../../src/main/java/com/ple/visur/MainVerticle.java#L1-50)] — deploys ModelWasChangedVerticle, CanvasWasChangedVerticle, KeyWasPressedVerticle, and HTTP server
[^2]: [[MainVerticle.java](../../src/main/java/com/ple/visur/MainVerticle.java#L51-100)] — configures StompEventBusBridge for WebSocket communication
