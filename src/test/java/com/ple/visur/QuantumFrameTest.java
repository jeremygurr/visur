package com.ple.visur;

import io.vertx.rxjava3.core.Vertx;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Which of the two quanta is the frame cannot depend on how much text is highlighted.
 *
 * <p>{@code ] w} ranks the cursor quantum against the operand quantum and treats the longer one as the frame: the jump
 * walks to the frame's end and selects the other quantum there. Ranking by measured length is a proxy for nesting, and
 * it holds while the two are measured at span 1. Measured at the live span it can come out the other way — the two
 * then measure equal across a run of blank lines, and an equal ranking hands the frame to the operand.
 *
 * <p>So the same two keys leave the cursor in a different quantum depending on the size of the selection, which is
 * what {@code s} must not be able to change.
 */
class QuantumFrameTest {
  private static final String A_LINE_OF_WORDS = "alpha beta gamma delta\n"; // 0..22
  private static final String TWENTY_FIVE_BLANK_LINES = "\n".repeat(25); // 23..48, one character each
  private static final String A_LINE_OF_SHORT_WORDS = "one two three four five six seven eight nine ten\n";

  private static final int A_BLANK_LINE = 30; // the start of the eighth line, inside the blank run
  private static final int BLANK_LINE_NUMBER = 8;
  private static final int COLUMN_IN_THAT_LINE = 0;

  private Vertx vertx;
  private EditorModelCoupler emc;
  private Quantum character;

  @BeforeEach
  void setUp() {
    vertx = Vertx.vertx();
    ServiceHolder.init(vertx.sharedData());
    ServiceHolder.initializerService.initializeEditorModel();
    emc = ServiceHolder.editorModelCoupler;
    emc.setSourceFilePath(""); // keep the swap-file hook away from the working tree
    emc.putEditorContent(A_LINE_OF_WORDS + TWENTY_FIVE_BLANK_LINES + A_LINE_OF_SHORT_WORDS);
    emc.putCanvasWidth(80);
    emc.putCanvasHeight(24);
    character = emc.getQuantumNameToQuantum().get("character");
    emc.putCursorQuantum(character);
    emc.putCX(COLUMN_IN_THAT_LINE);
    emc.putCY(BLANK_LINE_NUMBER);
    emc.putVirtualCX(COLUMN_IN_THAT_LINE);
    emc.putCanvasStart(0);
    emc.putFY(BLANK_LINE_NUMBER);
    emc.putCA(A_BLANK_LINE);
    assertEquals(A_BLANK_LINE, emc.getCA()); // cx and cy agree with ca, so nothing recomputes it under the jump
  }

  @AfterEach
  void tearDown() {
    vertx.close();
  }

  @Test
  void jumpingToEndOfAWordAtSpanOneLeavesYouInTheWordQuantum() {
    jumpToEndOfWord(1); // one character measures more than a word that is not there, so the character quantum frames
    assertEquals("word", emc.getCursorQuantum().getName());
  }

  @Test
  void jumpingToEndOfAWordAtSpanTwentyLeavesYouInTheWordQuantum() {
    jumpToEndOfWord(20); // twenty characters and twenty steps across blank lines measure the same
    assertEquals("word", emc.getCursorQuantum().getName());
  }

  @Test
  void jumpingToStartOfAWordAtSpanTwentyLeavesYouInTheWordQuantum() {
    jumpToStartOfWord(20); // the same ranking decides [ w, so it must not flip either
    assertEquals("word", emc.getCursorQuantum().getName());
  }

  /** The keystroke {@code ] w}: the submode pushes the operand's name, and the cursor quantum runs the jump. */
  private void jumpToEndOfWord(int span) {
    emc.putSpan(span);
    emc.putOnExecutionDataStack("word");
    character.quantumEnd();
  }

  /** The keystroke {@code [ w}. */
  private void jumpToStartOfWord(int span) {
    emc.putSpan(span);
    emc.putOnExecutionDataStack("word");
    character.quantumStart();
  }
}
