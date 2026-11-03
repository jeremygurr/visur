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
   * Pinned as it is today: the walk back from a cursor sitting on whitespace does not stop, so the word before the
   * cursor is selected rather than nothing. Whether that is right belongs to
   * <a href="../../../../../docs/wayfinder/span-and-count/issues/02-span-per-quantum.md">ticket 02</a>, which takes
   * the word quantum's span semantics as its subject.
   */
  @Test
  void aCursorOnWhitespaceSelectsTheWordBeforeIt() {
    String content = "one two";
    assertArrayEquals(new int[]{0, 3}, boundsAt(content, 3, 1));
  }

  @Test
  void emptyContentSelectsNothing() {
    assertArrayEquals(new int[]{0, 0}, boundsAt("", 0, 1));
    assertArrayEquals(new int[]{0, 0}, boundsAt("", 0, 0));
  }

  /**
   * Also pinned rather than endorsed: with no word in sight the walk to the next bound runs to the end of the
   * content, which leaves the whitespace itself selected. See ticket 02, same as above.
   */
  @Test
  void aSpanOfWordsInWhitespaceOnlyContentReachesTheEndOfTheContent() {
    assertArrayEquals(new int[]{0, 1}, boundsAt(" ", 0, 3));
  }

  @Test
  void computingBoundsLeavesTheLineIndicesAlone() {
    String content = "one two\nthree";
    ArrayList<Integer> nl = nextLineIndices(content);
    RegexQuantum.boundsFor(WORD, content, nl, 1, 0, 2);
    assertEquals(Arrays.asList(8, 13), nl);
  }
}
