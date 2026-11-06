package com.ple.visur;

import io.vertx.core.shareddata.Shareable;

/**
 * The one thing the cursor derives from, wrapped for the shared model, which only accepts shareable values.
 *
 * <p>It is the document's: where every line starts follows from the text and from nothing else, so every window's
 * cursor is wired to this one cell and an edit moves them all.
 */
public record NewlineIndicesCell(BrickVisurVar cell) implements Shareable {
}
