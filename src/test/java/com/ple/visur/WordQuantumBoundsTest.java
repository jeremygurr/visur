package com.ple.visur;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static com.ple.visur.NextLineIndicesFixture.nextLineIndices;

/**
 * Bounds of the word quantum at the pure seam {@link RegexQuantum#boundsFor}, with the pattern the model registers
 * for "word" ({@code \S+}, see {@code InitializerService#initializeQuantums}). A span of N words covers N words and
 * the whitespace between them, because the selection is one contiguous range.
 */
class WordQuantumBoundsTest {

  private static final Pattern WORD = Pattern.compile("\\S+");

  private int[] boundsAt(String content, int ca, int span) {
    return RegexQuantum.boundsFor(WORD, content, nextLineIndices(content), ca, 0, span);
  }

  @Test
  void oneSpanSelectsTheWordTheCursorSitsOn() {
    String content = "one two\nthree"; // nl is [8, 13]
    assertArrayEquals(new int[]{0, 3}, boundsAt(content, 0, 1));
    assertArrayEquals(new int[]{4, 7}, boundsAt(content, 4, 1));
    assertArrayEquals(new int[]{8, 13}, boundsAt(content, 10, 1));
  }

  @Test
  void aCursorInMiddleOfAWordSelectsTheWholeWord() {
    String content = "one two\nthree";
    assertArrayEquals(new int[]{0, 3}, boundsAt(content, 1, 1));
    assertArrayEquals(new int[]{0, 3}, boundsAt(content, 2, 1));
  }

  @Test
  void spanOfTwoCoversBothWordsAndTheWhitespaceBetweenThem() {
    String content = "one two\nthree";
    assertArrayEquals(new int[]{0, 7}, boundsAt(content, 0, 2));
  }

  @Test
  void aSpanOfWordsRunsAcrossALineBreak() {
    String content = "one two\nthree four"; // nl is [8, 19]
    assertArrayEquals(new int[]{4, 13}, boundsAt(content, 4, 2)); // "two\nthree"
  }

  @Test
  void aSpanLongerThanTheContentClampsAtItsEnd() {
    String content = "one two\nthree";
    assertArrayEquals(new int[]{0, 13}, boundsAt(content, 0, 3));
    assertArrayEquals(new int[]{0, 13}, boundsAt(content, 0, 9));
    assertArrayEquals(new int[]{8, 13}, boundsAt(content, 8, 4));
  }

  @Test
  void zeroSpanOnAWordStartSelectsNothing() {
    String content = "one two\nthree";
    assertArrayEquals(new int[]{4, 4}, boundsAt(content, 4, 0));
  }

  /**
   * Pinned as it is today, not as it should be: elsewhere a collapsed range is how "nothing selected" is spelled, and
   * <a href="../../../../../docs/wayfinder/span-and-count/issues/04-setspanop-no-clamp.md">ticket 04</a> settles that
   * rule in one place.
   */
  @Test
  void zeroSpanInsideAWordReachesFromTheWordStartToTheCursor() {
    String content = "one two\nthree";
    assertArrayEquals(new int[]{0, 1}, boundsAt(content, 1, 0));
  }

  /**
   * No word under the cursor means nothing selected, whatever the span — the same answer a blank line gives, which is
   * what makes {@code s} on an indent or between two words behave like {@code s} on an empty line instead of reaching
   * for a word somewhere else. The number survives; the range is empty
   * ({@link RelativeMoveOpTest}, where a move onto a blank line keeps span 1 and selects nothing).
   */
  @Test
  void aCursorOnWhitespaceSelectsNothing() {
    String content = "one two";
    assertArrayEquals(new int[]{3, 3}, boundsAt(content, 3, 1));
    assertArrayEquals(new int[]{3, 3}, boundsAt(content, 3, 2));
    String wideGap = "one   two"; // the space in the middle is three characters
    assertArrayEquals(new int[]{4, 4}, boundsAt(wideGap, 4, 3));
  }

  @Test
  void emptyContentSelectsNothing() {
    assertArrayEquals(new int[]{0, 0}, boundsAt("", 0, 1));
    assertArrayEquals(new int[]{0, 0}, boundsAt("", 0, 0));
  }

  @Test
  void aSpanOfWordsInWhitespaceOnlyContentSelectsNothing() {
    assertArrayEquals(new int[]{0, 0}, boundsAt(" ", 0, 3));
    assertArrayEquals(new int[]{0, 0}, boundsAt("  one", 0, 2), "an indent is not a word either");
  }

  /**
   * The gap between two words is one quantum walk, not one character per remaining span: {@code 2} words at a wide gap
   * used to select "one ", one word and a character of whitespace.
   */
  @Test
  void aSpanOfWordsCrossesAWideGap() {
    String content = "one   two   three"; // words at 0, 6 and 12, three characters of gap between them
    assertArrayEquals(new int[]{0, 9}, boundsAt(content, 0, 2), "one, the gap, two");
    assertArrayEquals(new int[]{0, content.length()}, boundsAt(content, 0, 3));
    assertArrayEquals(new int[]{6, content.length()}, boundsAt(content, 6, 2));
  }

  /** Trailing whitespace is not between two words, so a span that runs out of words stops at the end of the last one. */
  @Test
  void aSpanThatRunsOutOfWordsStopsAtTheLastOne() {
    String content = "one two   ";
    assertArrayEquals(new int[]{0, 7}, boundsAt(content, 0, 3));
  }

  @Test
  void computingBoundsLeavesTheLineIndicesAlone() {
    String content = "one two\nthree";
    ArrayList<Integer> nl = nextLineIndices(content);
    RegexQuantum.boundsFor(WORD, content, nl, 1, 0, 2);
    assertEquals(Arrays.asList(8, 13), nl);
  }
}
