---
type: Sources
title: Logback Configuration
description: Logback setup for Visur — a STDOUT console appender and per-package log levels
resource: src/main/resources/logback.xml
tags: [logging, configuration]
generated: { by: visur-wiki-updater/pi, at: 2026-10-13T22:05:00Z }
updated: { by: visur-wiki-updater/pi, at: 2026-10-13T22:05:00Z }
---

# Logback Configuration

**Source:** [[logback.xml](../../src/main/resources/logback.xml)]
**Date updated:** 2026-10-13

## Summary

Visur uses Logback for logging, configured by `logback.xml`. A single STDOUT appender writes to the console with the pattern `%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n`.[^1] Levels are set per package: two dependency loggers are quieted to `warn` (`com.mchange.v2`, `io.netty`), while the Vert.x loggers (`io.vertx`, `io.vertx.guides.wiki`) and Visur's own `com.ple.visur` logger run at `debug`. The root logger is `debug` and references the STDOUT appender.[^2]

## Key Takeaways

- One STDOUT console appender; no file or async appenders
- `com.mchange.v2` and `io.netty` are quieted to `warn`
- `io.vertx`, `io.vertx.guides.wiki`, and `com.ple.visur` are explicitly `debug`
- Root level is `debug`

## Entities & Concepts

- [[main-verticle](main-verticle.md)] — the server whose log output this configures
- [[abstract-visur-verticle](abstract-visur-verticle.md)] — verticles that log under `com.ple.visur`

## Relation to Other Wiki Pages

- [[main-verticle](main-verticle.md)] — starts the HTTP server and event bus that log through this configuration
- [[frontend-overview](frontend-overview.md)] — the browser side, which does not use Logback

## References

[^1]: [[logback.xml](../../src/main/resources/logback.xml#L4-8)] — STDOUT `ConsoleAppender` and its pattern
[^2]: [[logback.xml](../../src/main/resources/logback.xml#L10-18)] — per-package logger levels and the `debug` root
