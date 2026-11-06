package com.ple.visur;

import io.vertx.rxjava3.core.Vertx;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A count is typed <em>before</em> the key it belongs to, so it is not capped at one digit: {@code 12 s}.
 *
 * <p>The count is model state rather than a Java field because it spans keypresses and the renderers have to be able
 * to show it.
 */
class CountPrefixTest {
  private Vertx vertx;
  private EditorModelCoupler emc;

  @BeforeEach
  void setUp() {
    vertx = Vertx.vertx();
    ServiceHolder.init(vertx.sharedData());
    emc = ServiceHolder.editorModelCoupler;
    emc.putIsInCommandState(false);
    emc.putEditorSubmodeStack(navigate());
  }

  private static java.util.Stack<EditorSubmode> stackOf(EditorSubmode submode) {
    java.util.Stack<EditorSubmode> submodes = new java.util.Stack<>();
    submodes.push(submode);
    return submodes;
  }

  private java.util.Stack<EditorSubmode> navigate() {
    return stackOf(EditorSubmode.navigate);
  }

  @AfterEach
  void tearDown() {
    vertx.close();
  }

  @Test
  void aDigitBecomesThePendingCount() {
    assertTrue(CountPrefix.record(emc, "3"));
    assertEquals(3, emc.session().getPendingCount());
  }

  @Test
  void digitsAccumulateLeftToRight() {
    CountPrefix.record(emc, "3");
    CountPrefix.record(emc, "4");
    assertEquals(34, emc.session().getPendingCount());
  }

  @Test
  void aLeadingZeroKeepsCountingRatherThanRestarting() {
    CountPrefix.record(emc, "0");
    CountPrefix.record(emc, "5");
    assertEquals(5, emc.session().getPendingCount());
  }

  @Test
  void zeroIsACountAndNotTheAbsenceOfOne() {
    CountPrefix.record(emc, "0");
    assertEquals(0, emc.session().getPendingCount());
  }

  @Test
  void aKeyThatIsNotADigitIsNotConsumedByTheCount() {
    CountPrefix.record(emc, "3");
    assertFalse(CountPrefix.record(emc, "s"));
    assertEquals(3, emc.session().getPendingCount(), "the count is still for the key about to run");
  }

  @Test
  void aKeyThatIsNotADigitRetiresTheCountOnceItHasRun() {
    CountPrefix.record(emc, "3");
    CountPrefix.retireUnlessDigit(emc, "s");
    assertNull(emc.session().getPendingCount());
    CountPrefix.retireUnlessDigit(emc, "s"); // retiring twice is not an error
    assertNull(emc.session().getPendingCount());
  }

  @Test
  void aDigitTypedWhileTypingACommandIsCommandTextNotACount() {
    emc.putIsInCommandState(true);
    assertFalse(CountPrefix.record(emc, "7"));
    assertNull(emc.session().getPendingCount());
  }

  @Test
  void aDigitTypedInInsertModeIsTextNotACount() {
    emc.putEditorSubmodeStack(stackOf(EditorSubmode.insert));
    assertFalse(CountPrefix.record(emc, "7"));
    assertNull(emc.session().getPendingCount());
  }

  @Test
  void aDigitTypedInASubmodeIsNotACountEither() {
    // [ and ] and search each hold their own submode; a digit there belongs to that submode
    emc.putEditorSubmodeStack(stackOf(EditorSubmode.quantumEnd));
    assertFalse(CountPrefix.record(emc, "7"));
    assertNull(emc.session().getPendingCount());
  }

  @Test
  void aCountOfTwelveBeforeSsetsSpanToTwelve() {
    bootNavigateMode();
    press("1");
    press("2");
    press("s");
    assertEquals(12, emc.getSpan());
    assertNull(emc.session().getPendingCount(), "the key it belonged to used it up");
  }

  @Test
  void aCountBeforeAKeyThatDoesNotTakeOneIsDiscarded() {
    bootNavigateMode();
    press("3");
    press("q"); // q changes the cursor quantum to character, and takes no count
    assertNull(emc.session().getPendingCount());
    assertEquals("character", emc.getCursorQuantum().getName());
  }

  /** A model wired the way a session's is: keymaps compiled, quantum chosen, a document to select in. */
  private void bootNavigateMode() {
    ServiceHolder.initializerService.initializeEditorModel();
    emc.setSourceFilePath(""); // keep the swap-file hook away from the working tree
    emc.putEditorContent("one two three four five six seven eight nine ten\n");
    emc.putEditorSubmodeStack(navigate());
    emc.putCanvasWidth(80);
    emc.putCanvasHeight(24);
  }

  /** The verticle's own keypress handler, minus the bus: see {@link KeyWasPressedVerticle#press}. */
  private void press(String key) {
    KeyWasPressedVerticle.press(emc, KeyPressed.from(key), key);
    CountPrefix.retireUnlessDigit(emc, key);
  }

  @Test
  void aDigitTypedAfterSSetsNoSpan() {
    bootNavigateMode();
    press("s");
    assertEquals(1, emc.getSpan(), "bare s is one quantum");
    press("3");
    assertEquals(1, emc.getSpan(), "the digit is the start of the next count, not a revision of the last key");
    assertEquals(3, emc.session().getPendingCount());
  }

  @Test
  void thereIsNoSpanSubmodeToEnterAnymore() {
    // the digit used to be typed after s, which needed a keymap of its own; the count prefix replaced it
    assertNull(EditorSubmode.getSubmodeByString("span"));
  }
}
