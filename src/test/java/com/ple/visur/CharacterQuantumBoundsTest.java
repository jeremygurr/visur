package com.ple.visur;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static com.ple.visur.NextLineIndicesFixture.nextLineIndices;

/**
 * Bounds of the character quantum, computed at the pure seam {@link CharacterQuantum#boundsFor} — no server,
 * no shared data. See <a href="../../../../../docs/wayfinder/span-and-count/issues/01-bound-seam-and-tests.md">
 * ticket 01</a> for why the seam exists.
 *
 * <p>Indices follow the model's {@code nl} convention, spelled out in {@link NextLineIndicesFixture}.
 */
class CharacterQuantumBoundsTest {

  private static final String THREE_LINES = "one\ntwo\nthree";
  private static final int END = THREE_LINES.length(); // 13, the last nl entry: the content end

  private int[] boundsAt(String content, int ca, int span) {
    return CharacterQuantum.boundsFor(content, nextLineIndices(content), ca, 0, span);
  }


  @Test
  void oneSpanSelectsTheCharacterUnderTheCursor() {
    assertArrayEquals(new int[]{0, 1}, boundsAt(THREE_LINES, 0, 1));
    assertArrayEquals(new int[]{5, 6}, boundsAt(THREE_LINES, 5, 1));
  }

  @Test
  void zeroSpanSelectsNothing() {
    assertArrayEquals(new int[]{0, 0}, boundsAt(THREE_LINES, 0, 0));
    assertArrayEquals(new int[]{5, 5}, boundsAt(THREE_LINES, 5, 0));
  }

  @Test
  void spanOfTwoReachesTheNextCharacter() {
    assertArrayEquals(new int[]{0, 2}, boundsAt(THREE_LINES, 0, 2));
  }

  @Test
  void aSpanCrossesALineBreakBecauseWrappingIsADisplayConcern() {
    // ca 2 is the 'e' of "one"; span 4 covers "e\nt" — the newline is inside the selection, not a wall
    assertArrayEquals(new int[]{2, 6}, boundsAt(THREE_LINES, 2, 4));
  }

  @Test
  void theSelectionClampsAtTheEndOfTheContent() {
    assertArrayEquals(new int[]{END - 1, END}, boundsAt(THREE_LINES, END - 1, 1));
    assertArrayEquals(new int[]{END - 1, END}, boundsAt(THREE_LINES, END - 1, 5));
  }

  @Test
  void theCursorSittingAtTheEndOfTheContentSelectsNothing() {
    assertArrayEquals(new int[]{END, END}, boundsAt(THREE_LINES, END, 1));
    assertArrayEquals(new int[]{END, END}, boundsAt(THREE_LINES, END, 3));
  }

  @Test
  void anEmptyDocumentSelectsNothingAtAnySpan() {
    assertArrayEquals(new int[]{0, 0}, boundsAt("", 0, 0));
    assertArrayEquals(new int[]{0, 0}, boundsAt("", 0, 1));
    assertArrayEquals(new int[]{0, 0}, boundsAt("", 0, 4));
  }

  @Test
  void aSelectionOfOnlyTheTrailingNewlineStillSelectsIt() {
    String content = "ab\n"; // nl is [3, 3]; the content end is 3, so the newline at 2 is selectable
    assertArrayEquals(new int[]{2, 3}, boundsAt(content, 2, 1));
  }

  @Test
  void computingBoundsLeavesTheLineIndicesAlone() {
    ArrayList<Integer> nl = nextLineIndices(THREE_LINES);
    CharacterQuantum.boundsFor(THREE_LINES, nl, 5, 1, 3);
    assertEquals(Arrays.asList(4, 8, 13), nl);
  }
}
