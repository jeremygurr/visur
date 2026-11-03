package com.ple.visur;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static com.ple.visur.NextLineIndicesFixture.nextLineIndices;

/**
 * Bounds of the document quantum at the pure seam {@link DocumentQuantum#boundsFor}. The document quantum selects
 * the whole document at any span of one or more: with a single document there is no second document to reach, so a
 * larger span is the same selection rather than an error — see
 * <a href="../../../../../docs/wayfinder/span-and-count/issues/02-span-per-quantum.md">ticket 02</a>.
 */
class DocumentQuantumBoundsTest {

  private static final String THREE_LINES = "one\ntwo\nthree"; // 13 characters, nl is [4, 8, 13]

  private int[] boundsAt(String content, int ca, int span) {
    return DocumentQuantum.boundsFor(content, nextLineIndices(content), ca, 0, span);
  }

  @Test
  void oneSpanSelectsTheWholeDocumentFromAnywhereInTheContent() {
    assertArrayEquals(new int[]{0, 13}, boundsAt(THREE_LINES, 0, 1));
    assertArrayEquals(new int[]{0, 13}, boundsAt(THREE_LINES, 7, 1));
  }

  @Test
  void anySpanAboveOneSelectsTheSameWholeDocument() {
    assertArrayEquals(new int[]{0, 13}, boundsAt(THREE_LINES, 7, 2));
    assertArrayEquals(new int[]{0, 13}, boundsAt(THREE_LINES, 7, 50));
  }

  @Test
  void zeroSpanAtTheEndOfTheContentSelectsNothing() {
    assertArrayEquals(new int[]{13, 13}, boundsAt(THREE_LINES, 13, 0));
  }

  @Test
  void zeroSpanInsideTheContentCollapsesToTheStartOfTheDocument() {
    assertArrayEquals(new int[]{0, 0}, boundsAt(THREE_LINES, 7, 0));
  }

  @Test
  void anEmptyDocumentSelectsNothingAtAnySpan() {
    assertArrayEquals(new int[]{0, 0}, boundsAt("", 0, 0));
    assertArrayEquals(new int[]{0, 0}, boundsAt("", 0, 1));
    assertArrayEquals(new int[]{0, 0}, boundsAt("", 0, 3));
  }

  @Test
  void aDocumentOfOnlyANewlineIsThatOneCharacter() {
    assertArrayEquals(new int[]{0, 1}, boundsAt("\n", 0, 1));
  }

  @Test
  void computingBoundsLeavesTheLineIndicesAlone() {
    ArrayList<Integer> nl = nextLineIndices(THREE_LINES);
    DocumentQuantum.boundsFor(THREE_LINES, nl, 7, 0, 2);
    assertEquals(Arrays.asList(4, 8, 13), nl);
  }
}
