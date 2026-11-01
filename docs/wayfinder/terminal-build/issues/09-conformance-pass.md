# Conformance pass: measure both renderers against the spec

Type: task
Status: open
Blocked by: 06, 07, 08, 01

## Work

The ticket that decides whether the destination is actually reached.

Measure, at 100 KB / 1 MB / 5 MB and at two viewport sizes, for both renderers:

| Measure | Spec target | Where |
|---|---|---|
| keystroke → last painted pixel / cell, canvas | ≤ 16 ms at 5 MB | spec §4.3 |
| keystroke → last cell, terminal | ≤ 16 ms at 5 MB, tending to 1–2 ms | spec §5 |
| payload bytes per keystroke | flat in document size | spec §1 |
| paint time, canvas | ~1.6–2.4 ms, viewport-bounded | baseline ticket |
| paint time, terminal | inside the diff/full paths measured in 06 | ticket 06 |

Then go through the spec and mark every normative statement as **met**, **not met**, or **not yet implemented**. Anything "not met" is either a fix or a recorded contradiction with a ticket number — not a footnote.

Also test what the spec asserts but nobody has tried: resize during a search submode, a document shorter than one screen, a document with no trailing newline, and a cursor at the very end of the buffer (`cursorRow >= rows.length`, which renderers must clamp).

## Done when

- The measurement table is filled in with real numbers and the harness that produced it is committed.
- Every normative statement in the spec has a verdict.
- Any spec corrections are made in the spec itself, with the reason.
- The map can close: the destination is either met or its gaps are explicitly ticketed.
