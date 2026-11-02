package com.ple.visur;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DocumentJumpOpTest {
  @Test
  void bottomIsTheFirstCharacterOfTheLastNonPhantomLine() {
    assertEquals(8, DocumentJumpOp.bottomLineStart("one\ntwo\nthree"));
    assertEquals(8, DocumentJumpOp.bottomLineStart("one\ntwo\nthree\n"));
    assertEquals(0, DocumentJumpOp.bottomLineStart("one\n"));
    assertEquals(0, DocumentJumpOp.bottomLineStart(""));
  }
}
