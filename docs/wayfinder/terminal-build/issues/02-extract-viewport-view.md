# Extract `Viewport` and `View` against the current payload

Type: task
Status: closed
Closed: 2026-11-01

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

## Result

- **[`Viewport`](../../../../src/main/java/com/ple/visur/Viewport.java)** — two pure functions: `rows(content, width, origin, height)` lays out, `position(rows, index)` maps an absolute index to `(row, col)`. No `emc`, no bus, no statics. Eleven unit tests.
- **[`View`](../../../../src/main/java/com/ple/visur/View.java)** — a record with `of(emc)` (project) and `render()` (serialise). Layout is delegated, not reimplemented.
- **[`ModelWasChangedVerticle`](../../../../src/main/java/com/ple/visur/ModelWasChangedVerticle.java)** — five lines: consume, `View.of(emc).render()`, send. Its mutable `View` field and `toJson()` are gone.
- **[`EditorModelCoupler.getSearchPrompt()`](../../../../src/main/java/com/ple/visur/EditorModelCoupler.java)** — the prompt text, with the caret moved to the renderer.

### The spec was wrong about the invariant — in a good way

Spec §3.1 said `Viewport` may assume `origin` is wrap-aligned (`lineStart + k*width`), following `decrementCanvasStart`. **It does not need to.** The browser's wrap is a pure function of (content, width, where painting starts), so slicing from an arbitrary origin reproduces exactly what it drew — including after a resize, when `canvasStart` stops being a multiple of the new width. `Viewport` is therefore origin-agnostic, and a mid-row origin is a *tested* case (`startsAtAnOriginThatIsNotARowBoundary`) rather than a violated precondition. Spec §3.1 updated.

### Findings worth carrying forward

1. **"No wire change" was only half true.** Adding `rows` is additive, but the browser still paints from `editorContent`, `canvasStart` and `canvasEnd`, so those had to stay: removing them blanked the canvas. Ticket 02 is therefore additive and ticket 03 becomes subtractive + painter rewrite. Verification caught this twice (`editorContent`, then `canvasStart`) — no unit test could have.
2. **A full-width line followed by a newline costs two rows**, because the browser advances `y` when it draws the newline. This is tested (`aFullWidthLineFollowedByANewlineSpendsARowOnTheNewline`), and `getCanvasEnd`'s walk disagrees — it counts one row. The two can differ by up to a row, so until ticket 03 deletes that walk the browser can paint one row past the bottom (harmlessly clipped). The cross-check in the harness tolerates exactly one row and flags more.
3. **`position()` had a real bug, caught by a test:** at a wrapped boundary, `row.end() == nextRow.start()`, and the obvious `<=` put the cursor at column *width* — a cell that does not exist. A newline boundary belongs to the end of its row; a wrap boundary belongs to the start of the next.
4. **At page load the first view sometimes does not arrive until a key is pressed.** `visur.js` registers a handler on `modelWasChanged` and echoes it, and the event bus delivers a `send()` to one handler, so it round-robins against `ModelWasChangedVerticle`. Pre-existing, and it matters twice: `full`/resize work in ticket 04, and the launcher's readiness handshake in ticket 08, which waits for a first `viewWasChanged`.
5. `full` is always `false`; nothing needs it yet, and the resize path that would own the decision is ticket 04.

### Verified

17 unit tests, plus the app driven in headless Chromium at 112x45 cells, 6 KB / 1 MB / 5 MB documents, ~92 views including 80 lines of scrolling: rows never contain a newline, never exceed the viewport width, always advance, match the document text at their origins, bound to 44 rows, and the cursor sits inside its row. `origin` moved to 3940 during scrolling, so follow-the-cursor windowing works. The app painted 146k–243k pixels in every run.

The harness is `.scratch/verify-rows/verify.cjs`, uncommitted like the baseline harness it follows; the method is
  described above so it can be rebuilt, and the two traps it hit are worth knowing before rebuilding it: a second
event-bus *connection* round-robins against the app's handler, and a second *local* handler on the same connection
replaces it. Intercept the socket (`page.on('websocket')`) instead.
