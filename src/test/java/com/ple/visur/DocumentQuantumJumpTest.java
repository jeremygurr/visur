package com.ple.visur;

import io.vertx.rxjava3.core.Vertx;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@code [ r} and {@code ] r} are how the document ends get reached now that {@code gg} and {@code G} are gone, so they
 * have to land on those ends by themselves, with a selection and without one.
 *
 * <p>The content has no trailing line break and a final line long enough to tell apart the two answers this jump
 * replaced: {@code G} landed on the start of "conclusion", and {@code ] r} lands on the end of the content.
 */
class DocumentQuantumJumpTest {
  private static final String CONTENT = "one two three\nfour five six\nconclusion";
  private static final int LAST_CHARACTER = CONTENT.length() - 1;
  private static final int MIDDLE_OF_CONTENT = CONTENT.indexOf("five");

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
    // inside "five" on line two, so neither end is where the cursor already is
    cursorAt(MIDDLE_OF_CONTENT, MIDDLE_OF_CONTENT - CONTENT.lastIndexOf('\n', MIDDLE_OF_CONTENT) - 1, 1);
    chooseCursorQuantum("character");
  }

  @AfterEach
  void tearDown() {
    vertx.close();
  }

  @Test
  void theStartOfTheDocumentIsItsFirstCharacter() {
    press("[");
    press("r");
    assertEquals(0, emc.getCA());
  }

  @Test
  void theStartOfTheDocumentIsItsFirstCharacterWithNothingSelected() {
    press("0");
    press("s");
    press("[");
    press("r");
    assertEquals(0, emc.getCA());
    assertEquals(0, emc.getCursorQuantumEnd(), "and nothing is selected on arrival");
  }

  /**
   * The loss ticket 09 records: {@code G} landed on the start of the last line, which is a different character from the
   * end whenever the document does not end in a line break.
   */
  @Test
  void theEndOfTheDocumentIsItsLastCharacter() {
    press("]");
    press("r");
    assertEquals(LAST_CHARACTER, emc.getCA());
  }

  /** With nothing selected the caret sits one past the last character, which is where the character quantum lets it go. */
  @Test
  void theEndOfTheDocumentWithNothingSelectedIsPastItsLastCharacter() {
    press("0");
    press("s");
    press("]");
    press("r");
    assertEquals(CONTENT.length(), emc.getCA());
    assertEquals(CONTENT.length(), emc.getCursorQuantumEnd(), "and nothing is selected on arrival");
  }

  /**
   * The second {@code ] r} has the document quantum on both sides of the ranking, and the document quantum is the one
   * quantum whose answer is the whole content, so it is where a jump that selects nothing would go wrong.
   */
  @Test
  void jumpingToEndOfTheDocumentFromTheDocumentQuantumSelectsNothingAtSpanZero() {
    chooseCursorQuantum("document");
    press("0");
    press("s");
    press("]");
    press("r");
    assertEquals(CONTENT.length(), emc.getCA());
    assertEquals(CONTENT.length(), emc.getCursorQuantumEnd(), "the whole document is not suddenly selected");
  }

  @Test
  void jumpingToTheEndAndBackToTheStartRoundTrips() {
    press("]");
    press("r");
    press("[");
    press("r");
    assertEquals(0, emc.getCA());
  }

  /**
   * What "removed" has to mean here: no partial match, no state kept between the two presses, no stray letter doing
   * anything else. Verified by hand in both renderers too, since {@code g} arrives from the terminal as an ordinary
   * character and from the browser as one too.
   */
  @Test
  void ggAndUpperGDoNothingAtAll() {
    press("g");
    press("g");
    assertEquals(MIDDLE_OF_CONTENT, emc.getCA());
    press("G");
    assertEquals(MIDDLE_OF_CONTENT, emc.getCA());
    assertEquals(1, emc.getSpan(), "and nothing was selected on the way");
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
