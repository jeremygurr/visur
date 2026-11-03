package com.ple.visur;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static com.ple.visur.NextLineIndicesFixture.nextLineIndices;

/**
 * Bounds of the line quantum at the pure seam {@link LineQuantum#boundsFor}. A span of N lines covers N lines,
 * running to the end of the {@code cy + N - 1}'th line and clamping at the end of the content.
 */
class LineQuantumBoundsTest {

  private static final String THREE_LINES = "one\ntwo\nthree"; // nl is [4, 8, 13]

  private int[] boundsAt(String content, int ca, int cy, int span) {
    return LineQuantum.boundsFor(content, nextLineIndices(content), ca, cy, span);
  }

  @Test
  void oneSpanSelectsTheWholeLineTheCursorSitsOn() {
    assertArrayEquals(new int[]{0, 3}, boundsAt(THREE_LINES, 1, 0, 1));
    assertArrayEquals(new int[]{4, 7}, boundsAt(THREE_LINES, 6, 1, 1));
  }

  @Test
  void theLastLineRunsToTheEndOfTheContentWithoutATrailingNewline() {
    assertArrayEquals(new int[]{8, 13}, boundsAt(THREE_LINES, 8, 2, 1));
  }

  @Test
  void spanOfTwoReachesTheEndOfTheNextLine() {
    assertArrayEquals(new int[]{4, 13}, boundsAt(THREE_LINES, 4, 1, 2));
    assertArrayEquals(new int[]{0, 7}, boundsAt(THREE_LINES, 0, 0, 2));
  }

  @Test
  void aSpanOfEveryLineSelectsTheWholeDocument() {
    assertArrayEquals(new int[]{0, 13}, boundsAt(THREE_LINES, 0, 0, 3));
  }

  @Test
  void aSpanLongerThanTheDocumentClampsAtTheEndOfTheContent() {
    assertArrayEquals(new int[]{8, 13}, boundsAt(THREE_LINES, 8, 2, 2));
    assertArrayEquals(new int[]{4, 13}, boundsAt(THREE_LINES, 4, 1, 9));
  }

  @Test
  void zeroSpanOnALineStartSelectsNothing() {
    assertArrayEquals(new int[]{4, 4}, boundsAt(THREE_LINES, 4, 1, 0));
  }

  @Test
  void zeroSpanInTheMiddleOfALineCollapsesToTheLineStart() {
    assertArrayEquals(new int[]{4, 4}, boundsAt(THREE_LINES, 6, 1, 0));
  }

  @Test
  void aLineEndingInANewlineEndsBeforeItWhenAnotherLineFollows() {
    // the newline at 3 belongs to the first line's selection: the second line starts at 4
    assertArrayEquals(new int[]{0, 3}, boundsAt("one\ntwo", 0, 0, 1));
  }

  @Test
  void thePhantomLineAfterATrailingNewlineSelectsNothing() {
    String content = "ab\n"; // nl is [3, 3]: cy 1 is the phantom line, which has no characters
    assertArrayEquals(new int[]{3, 3}, boundsAt(content, 3, 1, 1));
  }

  @Test
  void aSpanCoveringAWholeLineAndItsPhantomLineStopsAtTheEndOfTheContent() {
    String content = "ab\n";
    assertArrayEquals(new int[]{0, 3}, boundsAt(content, 0, 0, 2));
  }

  @Test
  void anEmptyDocumentSelectsNothingAtAnySpan() {
    assertArrayEquals(new int[]{0, 0}, boundsAt("", 0, 0, 0));
    assertArrayEquals(new int[]{0, 0}, boundsAt("", 0, 0, 1));
    assertArrayEquals(new int[]{0, 0}, boundsAt("", 0, 0, 3));
  }

  @Test
  void computingBoundsLeavesTheLineIndicesAlone() {
    ArrayList<Integer> nl = nextLineIndices(THREE_LINES);
    LineQuantum.boundsFor(THREE_LINES, nl, 5, 1, 2);
    assertEquals(Arrays.asList(4, 8, 13), nl);
  }
}
