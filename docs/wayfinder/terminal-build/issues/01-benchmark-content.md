# Make large documents loadable, so the latency budget can be measured

Type: task
Status: open

## Work

The spec's acceptance criterion is *keystroke → paint ≤ 16 ms at 5 MB*, and today **nothing can produce a 5 MB document**: there is no file I/O and no bulk-load path. Without this, every measurement after it is unfalsifiable.

Pick the cheapest repeatable mechanism. Candidates, in order of preference:

1. **A startup content path** — a system property or env var read by [`InitializerService`](../../../../src/main/java/com/ple/visur/InitializerService.java) that repeats the Declaration until a target byte count, as the baseline harness did ad hoc. Zero UI surface, zero protocol surface, one code path.
2. **A debug route** — e.g. `POST /debug/content` on `MainVerticle`, guarded to localhost. Useful later for remote driving, but it is a new surface to maintain.
3. **File I/O (P5)** — the real feature. Correct eventually, but it drags a feature into a measurement ticket and the destination does not need it.

Whatever is chosen must be **documented in the repo** (not reinvented per measurement) and must be able to produce 100 KB, 1 MB and 5 MB.

## Done when

`./gradlew run` can be started such that the editor holds ~100 KB, ~1 MB and ~5 MB of text, the method is written down where the next person finds it, and the numbers are reproducible across two runs.

## Notes

The baseline ticket already measured the *old* path at these sizes (6.5 KB payload → 5.29 MB payload, 52–122 ms per keystroke at 5 MB); this ticket exists so the *new* path can be measured the same way. Reuse its harness method rather than inventing one: [baseline measurements](../../renderer-delivery/issues/01-baseline-measurements.md).
