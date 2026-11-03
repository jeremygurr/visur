package com.ple.visur;

import java.util.ArrayList;

/**
 * Builds the {@code nl} list the way the running model does — see
 * {@code CursorPositionDC.EditorContentService#updateNextLineIndices}: one entry per line, at the index that starts
 * it, plus a final entry at {@code content.length()}. A trailing newline therefore contributes a phantom entry equal
 * to the content length ("one\ntwo\n" is {@code [4, 8, 8]}), which is what the quantum bounds code means by a
 * phantom line.
 *
 * <p>The fixture has to keep agreeing with production, so its convention is pinned in
 * {@link NextLineIndicesFixtureTest}.
 */
final class NextLineIndicesFixture {
  private NextLineIndicesFixture() {}

  static ArrayList<Integer> nextLineIndices(String content) {
    ArrayList<Integer> indices = new ArrayList<>();
    for (int i = 0; i <= content.length(); i++) {
      if (i < content.length()) {
        if (content.charAt(i) == '\n') {
          indices.add(i + 1);
        }
      } else {
        indices.add(i);
      }
    }
    return indices;
  }
}
