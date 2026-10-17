---
type: Concepts
title: planned-features
description: Features Visur's own docs name as future work — typed objects, multi-user editing, filesystem navigation, macro logic, and stream editing — none implemented yet
tags: [planned, roadmap]
sources:
  - resource: README.adoc
  - resource: todo
generated: { by: visur-wiki-updater/pi, at: 2026-10-13T22:05:00Z }
updated: { by: visur-wiki-updater/pi, at: 2026-10-13T22:05:00Z }
---

# planned-features

## Description

[[README.adoc](../../README.adoc)] lists five features as future work, and the repo-root [[todo](../../todo)] file adds a concrete plan for filesystem navigation. None of these is implemented — no `FilesystemView`, `FileQuantum`, `DirectoryQuantum`, or `NormalView` type exists anywhere in the source.[^1][^2]

**Type-sensitive editing** — edit objects rather than only text files, which the README says would also make the editor language-independent.

**Multi-user editing** — a client-server architecture where several people edit the same file at once, in the manner of Google Docs.

**Macro logic** — conditional expressions and arithmetic inside macro definitions, so macros can branch and compute.

**Interface independence** — the editor usable from a browser, a command line, or a graphical window.

**Stream editing** — edit live data streams (for example a network flow) instead of only static files.

The `todo` file narrows the filesystem direction: separate a normal movement view (`NormalView`?) from filesystem navigation (`FilesystemView`?), implement that distinction (probably in [[model-was-changed-verticle](model-was-changed-verticle.md)]), add a `FileQuantum` and a `DirectoryQuantum`, then let files be read and written.

## Key Takeaways

- Five future features are named in README.adoc; none is implemented
- `todo` plans filesystem navigation via a `NormalView`/`FilesystemView` split and `FileQuantum`/`DirectoryQuantum`
- Typed objects would rest on the data-class system; filesystem quantums would extend the quantum abstraction

## Appearances in Sources

- [[README.adoc](../../README.adoc#L53-58)] — the five "Future Visur Features"[^1]
- [[todo](../../todo#L1-5)] — filesystem navigation plan[^2]

## Related Concepts

- [[quantum-system](quantum-system.md)] — the planned `FileQuantum`/`DirectoryQuantum` would extend the quantum abstraction
- [[model-was-changed-verticle](model-was-changed-verticle.md)] — the `todo` proposes the view split there
- [[data-class-hierarchy](data-class-hierarchy.md)] — type-sensitive editing would build on the data-class system
- [[frontend-overview](frontend-overview.md)] — the "interface independent" goal is not met yet; the frontend is browser-only

## References

[^1]: [[README.adoc](../../README.adoc#L53-58)] — the five features listed under "Future Visur Features"
[^2]: [[todo](../../todo#L1-5)] — filesystem navigation, `FileQuantum`, and `DirectoryQuantum` todos
