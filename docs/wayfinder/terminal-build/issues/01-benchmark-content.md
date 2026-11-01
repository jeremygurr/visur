# Make large documents loadable, so the latency budget can be measured

Type: task
Status: closed
Closed: 2026-11-01

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

## Result

Option 1, the startup content path: [`SizedContent`](../../../../src/main/java/com/ple/visur/SizedContent.java) generates repeated ASCII text of at least the requested size, and [`InitializerService`](../../../../src/main/java/com/ple/visur/InitializerService.java) reads `VISUR_CONTENT_BYTES` and prints what it loaded, so nobody benchmarks the wrong document by accident.

```
VISUR_CONTENT_BYTES=5242880 ./gradlew run
VISUR_CONTENT_BYTES=5242880 -> 5242944 character document
```

An environment variable rather than a system property because Gradle forks the application JVM: `-D` on the Gradle command line lands in Gradle's JVM, while the environment is inherited.

**Verified** at three sizes, with the *browser* rendering each: 6,183 characters (default Declaration), 1,048,656, and 5,242,944. In every case the viewport stayed at 44 rows, `origin` at 0, and the app painted ~243k pixels — the document size reached the renderer, and the payload did not grow with it beyond the transitional `editorContent` (below).

**ASCII-only is deliberate**: character count equals byte count, so a size stated in bytes holds for the payload and for the renderer without conversion.

`SizedContent` is unit-tested for size, line endings, ASCII-ness, determinism and non-positive input. Its six tests live in `src/test/java/com/ple/visur/`, which Gradle compiles by default — note that the two pre-existing tests under `src/test/CursorPositionDC/` are in a directory Gradle does not compile, so `./gradlew test` had been silently running nothing until these tests arrived.

**Not measurable yet**: the payload is still O(document) because `editorContent` is still on the wire until [the payload switch](03-switch-payload.md). This ticket makes the measurement *possible*; taking it is that ticket's job.
