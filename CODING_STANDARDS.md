# Coding standards

Conventions this codebase follows that the code itself does not force. Tooling already enforces syntax and Gradle wiring; this file is the unwritten part.

## State

`EditorModelCoupler` is the only mutable editor model. Read it through `getX`, write it through `putX` (`putCA`, `putSpan`, `putCursorQuantumStartAndScroll`). Operators reach it through `ServiceHolder` and nothing else — `EditorContentService` and `EditorDataModel` behind it are wired at startup and stay private to `EditorModelCoupler`.

Writes that scroll use the `AndScroll` variant; the plain variant is for restoring a position the renderer already knows.

One key owns each piece of state, and the operators that read it leave it alone. `s` writes span and nothing else does:
`RelativeMoveOp`, `ChangeCursorQuantumOp` and `DeleteCursorQuantumOp` recompute the *range* at the span already in the
model. They used to share a `SpanCollapse` that also zeroed the span whenever that range came back empty, which made a
blank line a trap door at the word quantum — a range derived from span zero is always empty, so once the span fell to
zero nothing re-derived a selection. A number the user typed outlives the content it was typed against.

## Pure arithmetic goes in a static

A computation that needs no model state becomes a `static` method taking everything it reads as parameters. It is then testable without bootting a verticle, which is how `Viewport`, `SizedContent` and each quantum's `boundsFor` are tested.

Keep sibling statics on one shape so a caller can pass any of them the same arguments — all four `boundsFor(content, nl, ca, cy, span)` take the same five, and the ones that read fewer say so in `@param`. A parameter with no reason to exist gets deleted; one with a sibling's reason gets a comment.

## Moving logic out of a method that also wrote state

Compute in the static, apply the write in the caller, and mark it `// legacy side effect: <what it does>` when the write is not what the caller came for. Keeping the write visible is what makes it removable later — `DocumentQuantum` parking the cursor at the document start is one, `RegexQuantum` snapping the cursor to the quantum start is another.

A rule repeated in three places becomes one method. `"digits typed before a key are that key's count"` lives in [`CountPrefix`](src/main/java/com/ple/visur/CountPrefix.java), called from the one place every keypress passes through — [`KeyWasPressedVerticle`](src/main/java/com/ple/visur/KeyWasPressedVerticle.java) — because a `KeymapHandler` is only consulted when a key has no binding, and a count that survives the wrong key is a bug twice removed.

## Operators

`Operator.execute(Object opInfo)`, one behavior each, compiled from a key sequence. Operands arrive on `ExecutionDataStack` — a LIFO the operator pops, in reverse of the order they were pushed. Presence-only operands are read as `stack.size() > 0 && stack.pop() instanceof LiteralNumberOp`.

New code belongs in `com.ple.visur`. `CursorPositionDC` and `DataClass` are the model's plumbing and rarely change.

## Keys and submodes

Bindings are registered in `InitializerService.createKeymaps`, so that file is the source of truth for what a key does. [`docs/reference/keys.md`](docs/reference/keys.md) mirrors it; a key change updates both.

A submode is a `KeymapHandler` bound with `holdKey`, entered by `PushSubmodeOp` and left by `RemoveSubmodeOp`; its prompt is command-state content, so the renderer draws it without a new wire field.

Changing a key or a wire field means deleting the old path in the same commit — no dual bindings, no compatibility window.

## The wire

[`View`](src/main/java/com/ple/visur/View.java) is the only projection the renderers read. A new field lands in three places at once: `View`, [`visur.js`](src/main/resources/static/visur.js), and [`protocol.rs`](terminal/src/protocol.rs) plus its consumer [`screen.rs`](terminal/src/screen.rs). Both renderers draw the selection only when span is above 0, and both treat `[start, end)` as inclusive-start, exclusive-end.

## Comments

One line, stating the why the code cannot show. Stale pseudocode gets rewritten along with the code it describes, never left beside it — the pseudocode above `RegexQuantum.getBoundaries` described a span rule the method no longer applied.

## Tests

JUnit 5. `mockito-core` is declared in the build file and no test uses it; there are two live precedents instead.

When the behavior is arithmetic, test the static seam and boot nothing — [`ViewportTest`](src/test/java/com/ple/visur/ViewportTest.java), [`DocumentJumpOpTest`](src/test/java/com/ple/visur/DocumentJumpOpTest.java), the `*BoundsTest` set. That is why `boundsFor` is a static: a live model holds one span at a time, and a span test wants to try several.

When the behavior *is* the service wiring, boot it in `@BeforeEach` — `Vertx.vertx()` then `ServiceHolder.init(vertx.sharedData())`, a `@TempDir` for files — and drive the real [`EditorModelCoupler`](src/test/java/com/ple/visur/FileIoTest.java). That is the honest way to test file I/O and command state.

Expected values are literals, never recomputed from the code under test. A test name states the behavior it pins. When current behavior looks wrong and you are pinning it rather than fixing it, name the doubt in the test and link the ticket that settles it — see the `cursorOnWhitespaceSelectsTheWordBeforeIt` case in [`WordQuantumBoundsTest`](src/test/java/com/ple/visur/WordQuantumBoundsTest.java).

The Rust client tests the same way: the layout and painting decisions are pure functions in [`screen.rs`](terminal/src/screen.rs) with an inline `#[cfg(test)]` module, so a test can check what a frame would contain without a pty.

## Docs

Every reference from one document to another is a markdown link with a relative path. Absolute paths and bare names break on checkout, and a link that resolves is also a check that the target still exists.

Effort work lives in `docs/wayfinder/<effort>/map.md` plus `issues/NN-slug.md`; a ticket is `Type` / `Status` / `Closed` / `Blocked by` over `## Work`, `## Done when`, `## Notes`, and a closed ticket gains the result where reality differed from the plan. Settled decisions get one line in the map under `## Decisions so far` pointing at the ticket that holds the detail. Stable material — keys, protocols, usage — goes in `docs/reference/`, which is a mirror of the code and says so.
