# Extract `Viewport` and `View` against the current payload

Type: task
Status: open

## Work

Spec §3 and §7 step 1. A pure refactor with **no wire change**, which is what makes it safe to do first and verifiable by running the browser.

- **New `Viewport`** in `com.ple.visur`: a pure function of `(content, width, origin, height)` returning rows with origins. No `EditorModelCoupler`, no event bus, no static state.
- **`View`** stops being a 2-field holder and becomes the payload producer: `View.of(emc)` reads state, delegates layout to `Viewport`, `render()` returns the payload.
- **`ModelWasChangedVerticle`** loses its `View view` **field** (shared mutable state rewritten per message on a shared event loop) and its `toJson()`; it becomes a pipe.
- **`emc.getSearchPrompt()`** replaces the `searchTarget += "_"` concatenation.
- **Before relying on it: verify the wrap-alignment invariant** — spec §3.1 assumes `origin` is a line start or `lineStart + k*width`, which follows from [`decrementCanvasStart`](../../../../src/main/java/com/ple/visur/EditorModelCoupler.java#L354-L379). If it does not hold in some path, `Viewport` cannot slice from an arbitrary origin and the spec needs correcting — that is a finding, not a local workaround.

The payload shape does not change in this ticket: `editorContent` still goes out.

## Done when

- `Viewport` has unit tests: wrapping at width, line starts, an origin mid-line, an empty document, a document with no trailing newline.
- The browser client renders and edits exactly as before, with no `toJson()` left in the verticle and no mutable `View` field.
- The invariant check is recorded either as a passing test or as a contradiction found against the spec.
