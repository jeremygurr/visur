package com.ple.visur;

/**
 * Bulk content for measuring Visur against large documents.
 *
 * <p>The latency bar is stated at a document size (see {@code docs/wayfinder/renderer-delivery/spec.md} §4.3),
 * and nothing else in Visur can produce a large document: there is no file I/O. This generator exists so a
 * measurement is reproducible instead of ad hoc. It is not a feature and nothing user-facing reads it; see
 * {@link InitializerService} for how a run selects it.
 *
 * <p>Deliberately ASCII-only, so that a character count is also a byte count and the sizes stay comparable
 * across renderers and encodings.
 */
public final class SizedContent {

  private static final String PARAGRAPH =
    "He has refused his Assent to Laws, the most wholesome and necessary for the public.\n";

  private SizedContent() {
  }

  /**
   * Returns at least {@code bytes} characters of repeated text, ending on a line boundary.
   *
   * <p>At least, not exactly: stopping on a line boundary keeps the content well formed, and a benchmark
   * only cares that the document is at least the size it claims to be.
   */
  public static String of(int bytes) {
    if (bytes <= 0) {
      return "";
    }
    StringBuilder content = new StringBuilder(bytes + PARAGRAPH.length());
    while (content.length() < bytes) {
      content.append(PARAGRAPH);
    }
    return content.toString();
  }
}
