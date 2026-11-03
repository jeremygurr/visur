package com.ple.visur;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static com.ple.visur.NextLineIndicesFixture.nextLineIndices;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Pins the {@code nl} convention the quantum bounds tests are written against, so a fixture that drifts from
 * {@code EditorContentService#updateNextLineIndices} fails here instead of quietly invalidating every bounds test.
 */
class NextLineIndicesFixtureTest {
  @Test
  void oneEntryPerLinePlusOneAtTheEndOfTheContent() {
    assertEquals(Arrays.asList(4, 8, 13), nextLineIndices("one\ntwo\nthree"));
    assertEquals(Arrays.asList(13), nextLineIndices("one two three"));
  }

  @Test
  void aTrailingNewlineContributesAPhantomLineEqualToTheContentLength() {
    assertEquals(Arrays.asList(4, 8, 8), nextLineIndices("one\ntwo\n"));
  }

  @Test
  void anEmptyDocumentIsOneLineOfLengthZero() {
    assertEquals(Arrays.asList(0), nextLineIndices(""));
  }
}
