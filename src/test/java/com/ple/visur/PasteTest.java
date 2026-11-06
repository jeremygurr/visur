package com.ple.visur;

import io.vertx.rxjava3.core.Vertx;
import io.vertx.core.json.JsonObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@code p} replaces the selection and hands the register what it displaced; {@code P} inserts in front of the
 * selection without disturbing it.
 *
 * <p>Everything runs through keypresses, because the interesting part is not the insertion but what the selection
 * looks like at the moment the key lands — after a yank, after a move, after a delete — and each of those is a
 * command that leaves the model somewhere specific.
 *
 * <p>The two {@code P} tests read the {@link View} payload rather than the model alone: "the same three words are
 * still selected" is a claim about what gets drawn, and the renderers read {@code [start, end)} with span above 0.
 */
class PasteTest {
  /** Line starts: 0, 19, 28 (the blank line), 29. */
  private static final String CONTENT = "one two three four\nfive six\n\nlast line\n";

  private Vertx vertx;
  private EditorModelCoupler emc;

  @BeforeEach
  void setUp() {
    vertx = Vertx.vertx();
    ServiceHolder.init();
    ServiceHolder.initializerService.initializeEditorModel();
    emc = ServiceHolder.editorModelCoupler;
    emc.document().setSourceFilePath(""); // keep the swap-file hook away from the working tree
    emc.document().putContent(CONTENT);
    emc.session().putCanvasWidth(80);
    emc.session().putCanvasHeight(24);
    emc.session().putCanvasStart(0);
    cursorAt(0, 0, 0);
  }

  @AfterEach
  void tearDown() {
    vertx.close();
  }

  @Test
  void pReplacesTheSelectionAndTakesWhatItDisplaced() {
    chooseCursorQuantum("word");
    press("s"); // "one " selected
    press("y");
    press("l"); // "two " selected
    press("p");
    assertEquals("one one three four\nfive six\n\nlast line\n", emc.document().getContent(),
        "the selection was replaced by the register, cell for cell");
    assertEquals("two ", yankRegister(), "and the register took what the paste displaced");
  }

  @Test
  void dThenPPutsTheDeletedTextBackWhereTheCursorIsNow() {
    chooseCursorQuantum("word");
    press("s");
    press("y"); // register "one "
    press("d"); // "one " removed, the highlight left as the gap it made
    assertEquals("two three four\nfive six\n\nlast line\n", emc.document().getContent());
    assertEquals("one ", yankRegister());

    press("l"); // one word over: "three " is now selected
    press("p");
    assertEquals("two one four\nfive six\n\nlast line\n", emc.document().getContent(),
        "the paste lands where the cursor is now, not where the text was deleted from");
  }

  /**
   * Over nothing, {@code p} displaces nothing, so the register still holds what it held and the paste happens twice.
   * This is the whole of the {@code p p} case: were the empty range recorded, the second paste would be a paste of
   * the empty string.
   */
  @Test
  void pTwiceWithNoSelectionPastesTwice() {
    chooseCursorQuantum("character");
    press("0");
    press("s"); // span 0: the caret, not a selection
    yank("X");
    press("p");
    press("p");
    assertEquals("XX" + CONTENT, emc.document().getContent(), "two copies, because neither paste displaced anything");
    assertEquals("X", yankRegister(), "and the register was never blanked");
  }

  /** A blank line at the word quantum answers "nothing selected" with an empty range — paste still happens there. */
  @Test
  void pOnABlankLineAtTheWordQuantumStillPastes() {
    chooseCursorQuantum("word");
    cursorAt(28, 0, 2); // the blank line
    press("0");
    press("s");
    assertEquals(0, emc.session().getCursorQuantumEnd() - emc.session().getCursorQuantumStart(), "a blank line selects no word");
    yank("X");

    press("p");
    assertEquals("one two three four\nfive six\nX\nlast line\n", emc.document().getContent());
    assertEquals("X", yankRegister(), "the register survived a paste that displaced nothing");
  }

  @Test
  void threePInsertsThreeCopiesAndRecordsTheDisplacedTextOnce() {
    chooseCursorQuantum("word");
    press("3");
    press("s"); // "one two three " selected
    yank("X");
    press("3");
    press("p");
    assertEquals("XXXfour\nfive six\n\nlast line\n", emc.document().getContent(),
        "the count multiplies the copies, never the text removed");
    assertEquals("one two three ", yankRegister(), "the displaced text, once: the register is a place, not a count");
  }

  /** The count of zero is an operator that does not happen — including to the text it would have displaced. */
  @Test
  void zeroPDisplacesNothingAndDeletesNothing() {
    chooseCursorQuantum("word");
    press("3");
    press("s");
    yank("X");
    press("0");
    press("p");
    assertEquals(CONTENT, emc.document().getContent());
    assertEquals("X", yankRegister());
  }

  /** Paste leaves no selection behind: the next {@code p} or {@code d} meets the text the paste put there. */
  @Test
  void pFinishesWithNoSelectionDrawn() {
    chooseCursorQuantum("word");
    press("3");
    press("s");
    yank("X");

    press("p");
    JsonObject drawn = View.of(emc).render();
    assertEquals(0, drawn.getInteger("span"), "paste finishes at span 0, not the three the selection had");
    assertEquals(drawn.getInteger("cursorQuantumStart"), drawn.getInteger("cursorQuantumEnd"));
  }

  /**
   * {@code P} through the wire: span and the shifted range are what a renderer paints, so this asserts the painted
   * selection rather than a model field it happens to come from.
   */
  @Test
  void capitalPLeavesTheSameThreeWordsSelectedShiftedRight() {
    chooseCursorQuantum("word");
    press("3");
    press("s");
    yank("X");

    press("P");
    assertEquals("Xone two three four\nfive six\n\nlast line\n", emc.document().getContent(),
        "inserted in front of the selection, which is not disturbed");

    JsonObject drawn = View.of(emc).render();
    assertEquals(3, drawn.getInteger("span"));
    assertEquals(1, drawn.getInteger("cursorQuantumStart"));
    assertEquals(15, drawn.getInteger("cursorQuantumEnd"));
  }

  /** With nothing selected, {@code P} and {@code p} differ only in that {@code P} cannot disturb a selection. */
  @Test
  void capitalPWithNothingSelectedInsertsAtTheCaretAndStaysCollapsed() {
    chooseCursorQuantum("character");
    press("0");
    press("s");
    yank("X");

    press("P");
    assertEquals("X" + CONTENT, emc.document().getContent());

    JsonObject drawn = View.of(emc).render();
    assertEquals(0, drawn.getInteger("span"));
    assertEquals(drawn.getInteger("cursorQuantumStart"), drawn.getInteger("cursorQuantumEnd"),
        "no highlight drawn for a selection P never touched");
  }

  /** Puts a known string in the register without involving the operator under test. */
  private void yank(String text) {
    emc.session().putGlobalVar("yankRegister", ObjectVisurVar.make(text));
  }

  private String yankRegister() {
    VisurVar register = emc.session().getGlobalVar("yankRegister");
    return register == null ? null : (String) register.getVal();
  }

  private void chooseCursorQuantum(String quantumName) {
    emc.session().putCursorQuantum(emc.getQuantumNameToQuantum().get(quantumName));
  }

  /** cx and cy have to agree with ca, or the model recomputes ca from them while the command runs. */
  private void cursorAt(int ca, int cx, int cy) {
    emc.session().putCX(cx);
    emc.session().putCY(cy);
    emc.session().putVirtualCX(cx);
    emc.session().putFY(cy);
    emc.session().putCA(ca);
    assertEquals(ca, emc.session().getCA());
  }

  /** The verticle's own keypress handler, minus the bus: see {@link KeyWasPressedVerticle#press}. */
  private void press(String key) {
    KeyWasPressedVerticle.press(emc, KeyPressed.from(key), key);
    CountPrefix.retireUnlessDigit(emc, key);
  }
}
