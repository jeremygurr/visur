package com.ple.visur;

import io.vertx.rxjava3.core.Vertx;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * A count says how far, and the span says how much travels with you.
 *
 * <p>Before this, a jump walked as far as the span: {@code ] w} at span 3 was a three-word jump, so selecting more text
 * moved the cursor further for the same two keys. One press now moves one quantum, {@code 3 ]} moves three, and the span
 * only decides the width of the window that lands there. Movement scales the same way, in both axes.
 *
 * <p>Both read the count typed <em>before</em> the key, and a count belongs to the whole key sequence it opens — which
 * for a jump is two keypresses, {@code ]} and then the quantum key.
 */
class MovementCountTest {
  private static final String CONTENT = "one two three four five\nsix seven eight nine ten\nlast line\n";
  private static final int LAST_CHARACTER_OF_LINE_ONE = 22; // the "e" of "five", one before the newline that ends line one
  private static final int FOURTH_WORD = 14; // the "four" in line one
  /** Derived from the content rather than counted by hand, since a jump down is what the test is about. */
  private static final int START_OF_LINE_THREE = CONTENT.indexOf("last line");
  private static final int END_OF_LINE_THREE = CONTENT.lastIndexOf('\n') - 1; // the "e" of "line"

  private Vertx vertx;
  private EditorModelCoupler emc;

  @BeforeEach
  void setUp() {
    vertx = Vertx.vertx();
    ServiceHolder.init(vertx.sharedData());
    ServiceHolder.initializerService.initializeEditorModel();
    emc = ServiceHolder.editorModelCoupler;
    emc.setSourceFilePath(""); // keep the swap-file hook away from the working tree
    emc.putEditorContent(CONTENT);
    emc.putCanvasWidth(80);
    emc.putCanvasHeight(24);
    emc.putCanvasStart(0);
    emc.putSpan(1);
    cursorAt(0, 0, 0);
  }

  @AfterEach
  void tearDown() {
    vertx.close();
  }

  @Test
  void threeCharactersRight() {
    chooseCursorQuantum("character");
    press("3");
    press("l");
    assertEquals(3, emc.getCA());
  }

  @Test
  void threeWordsRight() {
    chooseCursorQuantum("word");
    press("3");
    press("l");
    assertEquals(FOURTH_WORD, emc.getCA());
    assertEquals(FOURTH_WORD, emc.getCursorQuantumStart());
    assertEquals(18, emc.getCursorQuantumEnd(), "and it selects the word it landed on");
  }

  @Test
  void twoLinesDown() {
    chooseCursorQuantum("character");
    press("2");
    press("j");
    assertEquals(START_OF_LINE_THREE, emc.getCA());
    assertEquals(2, emc.getCY());
  }

  @Test
  void twoLinesDownStaysInTheColumnAtTheWordQuantum() {
    chooseCursorQuantum("word");
    press("2");
    press("j");
    assertEquals(START_OF_LINE_THREE, emc.getCA());
    assertEquals(2, emc.getCY());
  }

  @Test
  void zeroIsAMovementThatDoesNotHappen() {
    chooseCursorQuantum("character");
    press("0");
    press("l");
    assertEquals(0, emc.getCA());
  }

  @Test
  void theArrowKeyTakesTheSameCountAsTheLetter() {
    chooseCursorQuantum("character");
    press("3");
    press("ArrowRight");
    assertEquals(3, emc.getCA());
  }

  @Test
  void theMovementUsesTheCountUp() {
    chooseCursorQuantum("character");
    press("3");
    press("l");
    assertNull(emc.getPendingCount(), "a count is spent by the key it belonged to");
  }

  @Test
  void aCountSurvivesTheKeyThatOpensASubmode() {
    press("3");
    press("]");
    assertEquals(3, emc.getPendingCount(), "] and w are one command; the count is for the pair");
  }

  @Test
  void aJumpFromTheLineQuantumAtSpanOneGoesToEndOfTheLine() {
    chooseCursorQuantum("line");
    press("]");
    press("q");
    assertEquals(LAST_CHARACTER_OF_LINE_ONE, emc.getCA());
    assertEquals(22, emc.getCursorQuantumStart());
    assertEquals(23, emc.getCursorQuantumEnd());
  }

  @Test
  void aJumpFromTheLineQuantumAtSpanFiveGoesToTheSamePlaceWider() {
    emc.putSpan(5);
    chooseCursorQuantum("line");
    press("]");
    press("q");
    assertEquals(LAST_CHARACTER_OF_LINE_ONE, emc.getCA(), "the span widens the selection, not the jump");
    assertEquals(22, emc.getCursorQuantumStart());
    assertEquals(27, emc.getCursorQuantumEnd());
  }

  @Test
  void threeJumpsFromTheLineQuantumGoThreeLinesDown() {
    chooseCursorQuantum("line");
    press("3");
    press("]");
    press("q");
    assertEquals(END_OF_LINE_THREE, emc.getCA());
    assertEquals(2, emc.getCY());
  }

  @Test
  void aCountOfZeroOnAJumpStillJumpsBecauseItHasToLandSomewhere() {
    chooseCursorQuantum("line");
    press("0");
    press("]");
    press("q");
    assertEquals(LAST_CHARACTER_OF_LINE_ONE, emc.getCA());
  }

  @Test
  void aJumpBackFromTheLineQuantumIsNotWiderAtALargerSpan() {
    jumpBackToStartOfLine();
    assertEquals(0, emc.getCA(), "at span 1");
    emc.putSpan(5);
    jumpBackToStartOfLine();
    assertEquals(0, emc.getCA(), "at span 5, and the span is not what decides how far back");
  }

  /** Mid-word, so there is somewhere to go back to. The jump leaves you in the quantum it selected, so the line quantum is chosen every time. */
  private void jumpBackToStartOfLine() {
    chooseCursorQuantum("line");
    cursorAt(10, 10, 0);
    press("[");
    press("q");
  }

  private void chooseCursorQuantum(String quantumName) {
    emc.putCursorQuantum(emc.getQuantumNameToQuantum().get(quantumName));
  }

  /** cx and cy have to agree with ca, or the model recomputes ca from them while the command runs. */
  private void cursorAt(int ca, int cx, int cy) {
    emc.putCX(cx);
    emc.putCY(cy);
    emc.putVirtualCX(cx);
    emc.putFY(cy);
    emc.putCA(ca);
    assertEquals(ca, emc.getCA());
  }

  /** The verticle's own keypress handler, minus the bus: see {@link KeyWasPressedVerticle#press}. */
  private void press(String key) {
    KeyWasPressedVerticle.press(emc, KeyPressed.from(key), key);
    CountPrefix.retireUnlessDigit(emc, key);
  }
}
