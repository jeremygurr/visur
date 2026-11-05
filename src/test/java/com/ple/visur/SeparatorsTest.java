package com.ple.visur;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class SeparatorsTest {
  @Test
  void includesTheWholeRunOfSpacesAfterAWord() {
    assertArrayEquals(new int[]{0, 6}, Separators.including("one   two", new int[]{0, 3}, " "));
  }

  @Test
  void includesATabAfterAWord() {
    assertArrayEquals(new int[]{0, 4}, Separators.including("one\ttwo", new int[]{0, 3}, " \t"));
  }

  @Test
  void doesNotIncludeANewlineAfterTheLastWord() {
    assertArrayEquals(new int[]{4, 7}, Separators.including("one\ntwo\n", new int[]{4, 7}, " \t"));
  }

  @Test
  void includesTheTerminatingNewlineAfterALine() {
    assertArrayEquals(new int[]{0, 4}, Separators.including("one\ntwo", new int[]{0, 3}, "\n"));
  }

  @Test
  void doesNotInventATerminatorForTheLastLine() {
    assertArrayEquals(new int[]{4, 7}, Separators.including("one\ntwo", new int[]{4, 7}, "\n"));
  }

  @Test
  void leavesAnEmptyRangeCollapsed() {
    assertArrayEquals(new int[]{3, 3}, Separators.including("one two", new int[]{3, 3}, " "));
  }

  @Test
  void extendsTheFarEndOnceForTheWholeSelection() {
    assertArrayEquals(new int[]{0, 12}, Separators.including("one   two   three", new int[]{0, 9}, " "));
  }
}
