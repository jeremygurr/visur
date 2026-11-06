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
 * it holds while the two are measured at span 1. Measured at the live span it can come out the other way — twenty words
 * past a run of blank lines out-measure the twenty lines that stop at it — and the same two keys then leave the cursor
 * in a different quantum depending on the size of the selection, which is what {@code s} must not be able to change.
 *
 * <p>The blank-line tests below pin the frame decision for a quantum that is <em>not there</em>, which is a different
 * rule: a word on a blank line measures nothing at any span, so the character quantum frames however wide the selection
 * is. {@link #twentyWordsOutMeasureTwentyLinesAndTheLineStillFrames()} is the one that fails if the ranking goes back
 * to the live span.
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
    ServiceHolder.init();
    ServiceHolder.initializerService.initializeEditorModel();
    emc = ServiceHolder.editorModelCoupler;
    emc.document().setSourceFilePath(""); // keep the swap-file hook away from the working tree
    emc.document().putContent(A_LINE_OF_WORDS + TWENTY_FIVE_BLANK_LINES + A_LINE_OF_SHORT_WORDS);
    emc.session().putCanvasWidth(80);
    emc.session().putCanvasHeight(24);
    character = emc.getQuantumNameToQuantum().get("character");
    emc.session().putCursorQuantum(character);
    emc.session().putCX(COLUMN_IN_THAT_LINE);
    emc.session().putCY(BLANK_LINE_NUMBER);
    emc.session().putVirtualCX(COLUMN_IN_THAT_LINE);
    emc.session().putCanvasStart(0);
    emc.session().putFY(BLANK_LINE_NUMBER);
    emc.session().putCA(A_BLANK_LINE);
    assertEquals(A_BLANK_LINE, emc.session().getCA()); // cx and cy agree with ca, so nothing recomputes it under the jump
  }

  @AfterEach
  void tearDown() {
    vertx.close();
  }

  @Test
  void jumpingToEndOfAWordAtSpanOneLeavesYouInTheWordQuantum() {
    jumpToEndOfWord(1); // one character measures more than a word that is not there, so the character quantum frames
    assertEquals("word", emc.session().getCursorQuantum().getName());
  }

  @Test
  void jumpingToEndOfAWordAtSpanTwentyLeavesYouInTheWordQuantum() {
    jumpToEndOfWord(20); // twenty characters still measure more than a word that is not here
    assertEquals("word", emc.session().getCursorQuantum().getName());
  }

  @Test
  void jumpingToStartOfAWordAtSpanTwentyLeavesYouInTheWordQuantum() {
    jumpToStartOfWord(20); // the same ranking decides [ w, so it must not flip either
    assertEquals("word", emc.session().getCursorQuantum().getName());
  }

  /** The keystroke {@code ] w}: the submode pushes the operand's name, and the cursor quantum runs the jump. */
  private void jumpToEndOfWord(int span) {
    emc.session().putSpan(span);
    emc.session().putOnExecutionDataStack("word");
    character.quantumEnd();
  }

  /** The keystroke {@code [ w}. */
  private void jumpToStartOfWord(int span) {
    emc.session().putSpan(span);
    emc.session().putOnExecutionDataStack("word");
    character.quantumStart();
  }

  /**
   * {@code ] q} on a one-character word lands on that character.
   *
   * <p>The two quanta measure the same length here — a word of one character — so the old tie fell to the operand
   * quantum and made it the frame, and the jump then selected a word where the key had asked for a character. Nesting
   * does not tie: a word contains a character whatever the word's length, and the spec says so.
   */
  @Test
  void jumpingToTheCharacterInAOneCharacterWordLeavesYouInTheCharacterQuantum() {
    emc.document().putContent("a b\n");
    emc.session().putCX(0);
    emc.session().putCY(0);
    emc.session().putVirtualCX(0);
    emc.session().putFY(0);
    emc.session().putCA(0);
    emc.session().putCursorQuantum(emc.getQuantumNameToQuantum().get("word"));
    emc.session().putSpan(1);
    emc.session().putOnExecutionDataStack("character");
    emc.getQuantumNameToQuantum().get("word").quantumEnd(); // the keystroke ] q, with words as the cursor quantum
    assertEquals("character", emc.session().getCursorQuantum().getName());
    assertEquals(1, emc.session().getCursorQuantumStart());
    assertEquals(2, emc.session().getCursorQuantumEnd());
  }

  /**
   * The inversion this ticket was written about, which only became reachable once the word walk crossed a gap in one
   * step rather than one character per remaining span — see
   * {@link WordQuantumBoundsTest#aSpanOfWordsCrossesAWideGap()}. Twenty words do out-measure twenty short lines: the
   * words run on into the paragraph past the blank run while the lines stop at it.
   *
   * <p>Ranked at the live span that hands the frame to the word quantum at span 20 and {@code ] e} leaves you in the
   * line quantum; ranked at span 1 the line is the frame because a line contains a word, which is the fact the proxy
   * was only ever trying to measure.
   */
  @Test
  void twentyWordsOutMeasureTwentyLinesAndTheLineStillFrames() {
    emc.document().putContent("alpha beta\n" + "\n".repeat(19)
      + "one two three four five six seven eight nine ten eleven twelve thirteen\n");
    emc.session().putCX(0);
    emc.session().putCY(0);
    emc.session().putVirtualCX(0);
    emc.session().putFY(0);
    emc.session().putCA(0);
    emc.session().putCursorQuantum(emc.getQuantumNameToQuantum().get("word"));
    emc.session().putSpan(20);
    emc.session().putOnExecutionDataStack("line");
    emc.getQuantumNameToQuantum().get("word").quantumEnd(); // the keystroke ] e, with words as the cursor quantum
    assertEquals("word", emc.session().getCursorQuantum().getName());
  }
}
