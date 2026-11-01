package com.ple.visur;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SizedContentTest {

  @Test
  void producesAtLeastTheRequestedNumberOfBytes() {
    assertTrue(SizedContent.of(1000).length() >= 1000);
    assertTrue(SizedContent.of(1_048_576).length() >= 1_048_576);
  }

  @Test
  void endsWithANewline() {
    assertEquals('\n', SizedContent.of(500).charAt(SizedContent.of(500).length() - 1));
  }

  @Test
  void isAsciiSoCharsAndBytesAgree() {
    String content = SizedContent.of(500);
    for (int i = 0; i < content.length(); i++) {
      assertTrue(content.charAt(i) < 128, "non-ascii at " + i);
    }
  }

  @Test
  void isDeterministic() {
    assertEquals(SizedContent.of(500), SizedContent.of(500));
  }

  @Test
  void containsManyLinesSoLineStartMathIsExercised() {
    String content = SizedContent.of(1000);
    long lines = content.chars().filter(c -> c == '\n').count();
    assertTrue(lines >= 10, "expected several lines, got " + lines);
  }

  @Test
  void emptyForNonPositiveRequests() {
    assertEquals("", SizedContent.of(0));
    assertEquals("", SizedContent.of(-5));
  }
}
