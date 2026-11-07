package com.ple.visur;

import io.vertx.rxjava3.core.Vertx;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Enter in insert mode splits a line, and the caret goes to the line it made — not the one it left.
 *
 * <p>The row is not derived from the character offset in this model: the movements that cross a line own it, and a text
 * insertion never changes it, because a character never moves you to a new row. Splitting a line does, so
 * {@link InsertNewlineOp} has to say so or the view paints the caret where the newline used to be.
 */
class InsertNewlineTest {
  private Vertx vertx;
  private EditorModelCoupler emc;

  @BeforeEach
  void setUp() {
    vertx = Vertx.vertx();
    ServiceHolder.init();
    ServiceHolder.initializerService.initializeEditorModel();
    emc = ServiceHolder.editorModelCoupler;
    emc.document().setSourceFilePath(""); // keep the swap-file hook away from the working tree
    emc.session().putCanvasWidth(80);
    emc.session().putCanvasHeight(24);
    emc.session().putCanvasStart(0);
    emc.session().putSpan(0);
    chooseCursorQuantum("character");
  }

  @AfterEach
  void tearDown() {
    vertx.close();
  }

  /** The report: the line appears below, and the caret stays a row above until you type a character. */
  @Test
  void enteringAtTheEndOfALinePutsTheCaretOnTheLineItOpened() {
    contentAndCursorAt("ab\ncd", 2, 0);

    press("i");
    press("Enter");

    assertEquals("ab\n\ncd", emc.document().getContent());
    assertEquals(3, emc.session().getCA(), "just past the newline, which is the start of the line below");
    assertEquals(1, caret()[0], "the caret belongs on the row the typing will land on");
    assertEquals(0, caret()[1], "at its start");
  }

  /** Splitting in the middle: the tail moves down, and the caret is the first character of the tail. */
  @Test
  void enteringInTheMiddleOfALinePutsTheCaretOnTheMovedTail() {
    contentAndCursorAt("abcd", 1, 0);

    press("i");
    press("Enter");

    assertEquals("a\nbcd", emc.document().getContent());
    assertEquals(2, emc.session().getCA());
    assertEquals(1, caret()[0]);
    assertEquals(0, caret()[1]);
  }

  /** Typing after the Enter has to land where the caret was drawn, which is the whole complaint. */
  @Test
  void typingAfterAnEnterGoesWhereTheCaretWasDrawn() {
    contentAndCursorAt("ab\ncd", 2, 0);

    press("i");
    press("Enter");
    int rowBeforeTyping = caret()[0];
    press("x");

    assertEquals("ab\nx\ncd", emc.document().getContent(), "the character is on the new line");
    assertEquals(rowBeforeTyping, caret()[0], "and the caret never moved to get there");
  }

  /**
   * The report, exactly: Enter at the end of the last line opens a line, and the caret does not go to it.
   *
   * <p>The caret is not a stored row — it is where the viewport's rows put the cursor offset — so this is about the
   * empty line the newline opened having no row to stand on. Typing a character made the caret arrive, which is how
   * it looks like a paint that missed a beat rather than a line that was not laid out.
   */
  @Test
  void enteringAtTheEndOfTheFilePutsTheCaretOnTheLineItOpened() {
    contentAndCursorAt("ab", 2, 0);

    press("i");
    press("Enter");

    assertEquals("ab\n", emc.document().getContent());
    assertEquals(3, emc.session().getCA());
    assertEquals(1, caret()[0], "the empty line a final newline opens is a row the caret can stand on");
    assertEquals(0, caret()[1]);
  }

  /** The row counts wrapped lines too, and splitting a long line is the case where a naive +1 could go wrong. */
  @Test
  void enteringInsideAWrappedLinePutsTheCaretOnTheRowBelow() {
    emc.session().putCanvasWidth(10);
    contentAndCursorAt("aaaaaaaaaaaaaaaaaaaa", 12, 1, 2, 0);

    press("i");
    press("Enter");

    assertEquals("aaaaaaaaaaaa\naaaaaaaa", emc.document().getContent());
    assertEquals(2, caret()[0], "the caret drops one row, and the rows below it shift down with it");
    assertEquals(0, caret()[1]);
  }

  /** Where the terminal cursor will go, as the view sends it: a (row, column) inside the window. */
  private int[] caret() {
    View view = View.of(emc, false);
    return new int[]{view.cursorRow(), view.cursorCol()};
  }

  private void contentAndCursorAt(String content, int ca, int cy) {
    contentAndCursorAt(content, ca, cy, ca, cy);
  }

  /** cx is a column within the row and fy is a logical line, so a wrapped cursor needs its own of each. */
  private void contentAndCursorAt(String content, int ca, int cy, int cx, int fy) {
    emc.document().putContent(content);
    emc.session().putCX(cx);
    emc.session().putCY(cy);
    emc.session().putVirtualCX(ca);
    emc.session().putFY(fy);
    emc.session().putCA(ca);
  }

  private void chooseCursorQuantum(String quantumName) {
    emc.session().putCursorQuantum(emc.getQuantumNameToQuantum().get(quantumName));
  }

  /** See {@link InsertRepeatTest}: the verticle's own keypress handler, minus the bus. */
  private void press(String key) {
    KeyWasPressedVerticle.press(emc, KeyPressed.from(key), key);
    CountPrefix.retireUnlessDigit(emc, key);
  }
}
