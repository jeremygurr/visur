package com.ple.visur;

import io.vertx.rxjava3.core.Vertx;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A count in front of an insert-entering key repeats what you typed, vim's {@code 3o}: one line is opened, you type
 * once, and Escape writes the other copies.
 *
 * <p>Keypress-level throughout. The whole point is a sequence — a count, a key, some typing, Escape — and the state
 * that makes it work (a stashed count, a recorded session, a caret) only ever exists between those presses.
 */
class InsertRepeatTest {
  /** Line starts: 0, 4, 8. */
  private static final String TWO_LINES = "one\ntwo\n";
  private static final int MIDDLE_OF_LINE_TWO = 5; // the "w" of "two"

  private Vertx vertx;
  private EditorModelCoupler emc;

  @BeforeEach
  void setUp() {
    vertx = Vertx.vertx();
    ServiceHolder.init(vertx.sharedData());
    ServiceHolder.initializerService.initializeEditorModel();
    emc = ServiceHolder.editorModelCoupler;
    emc.setSourceFilePath(""); // keep the swap-file hook away from the working tree
    emc.putEditorContent(TWO_LINES);
    emc.putCanvasWidth(80);
    emc.putCanvasHeight(24);
    emc.putCanvasStart(0);
    emc.putSpan(1);
    chooseCursorQuantum("line");
    cursorAt(0, 0, 0);
  }

  @AfterEach
  void tearDown() {
    vertx.close();
  }

  /** The headline case: one line opened, the typing repeated on Escape, the caret left on the copy it ended with. */
  @Test
  void threeGThenTypingAbcThenEscapeGivesThreeIdenticalLines() {
    cursorAt(MIDDLE_OF_LINE_TWO, 1, 1);

    press("3");
    press("g");
    type("abc");
    press("Escape");

    assertEquals("one\nabc\nabc\nabc\ntwo\n", emc.getEditorContent());
    assertEquals(15, emc.getCA(), "just past the last character of the third copy");
  }

  /**
   * With nothing typed the session's unit is the break the command opened with, so {@code 3 g} is three blank lines —
   * the thing the user asked for by name, which an empty-string repeat would silently refuse to do.
   */
  @Test
  void threeGWithNothingTypedGivesThreeBlankLines() {
    cursorAt(MIDDLE_OF_LINE_TWO, 1, 1);

    press("3");
    press("g");
    press("Escape");

    assertEquals("one\n\n\n\ntwo\n", emc.getEditorContent(), "one opened line and two more from the count");
  }

  /**
   * {@code 3 i} separates its copies with nothing: the rule is the session mechanism, not a {@code g} special case.
   *
   * <p>The caret is where Escape leaves it, which on this quantum is the start of the selection it restores rather than
   * the end of the third copy: {@code "abcabcabc"} typed by hand lands in the same place, because a word run has no
   * separator in it for the copies to be told apart by. See {@code g} above for a quantum where the caret does sit on
   * the last copy.
   */
  @Test
  void threeIThenTypingAbcThenEscapeGivesAbcAbcAbcAtTheStartOfTheWord() {
    emc.putEditorContent("one\ntwo");
    chooseCursorQuantum("word");
    cursorAt(MIDDLE_OF_LINE_TWO, 1, 1); // the "w", inside the word that starts at 4

    press("3");
    press("i");
    assertEquals(4, emc.getCA(), "i went to the start of the word, as it always does");
    type("abc");
    press("Escape");

    assertEquals("one\nabcabcabctwo", emc.getEditorContent());
    assertEquals(4, emc.getCA(), "where typing the same text by hand leaves it");
    assertEquals(16, emc.getCursorQuantumEnd(), "restored over the run the three copies made");
  }

  /** The fourth key of the mechanism: {@code 3 a} types at the end of the word and repeats from there. */
  @Test
  void threeAThenTypingAbcThenEscapeGivesAbcAbcAbcAfterTheWord() {
    emc.putEditorContent("one\ntwo");
    chooseCursorQuantum("word");
    cursorAt(MIDDLE_OF_LINE_TWO, 1, 1);

    press("3");
    press("a");
    type("abc");
    press("Escape");

    assertEquals("one\ntwoabcabcabc", emc.getEditorContent());
  }

  /** A session that spanned a newline repeats whole: the unit is one session's worth of text, not one line of it. */
  @Test
  void aMultiLineSessionRepeatsWhole() {
    cursorAt(1, 1, 0); // line one of "one\ntwo\n"

    press("3");
    press("G");
    type("one");
    press("Enter");
    type("two");
    press("Escape");

    assertEquals("one\n" + "one\ntwo\n".repeat(3) + "two\n", emc.getEditorContent(),
        "three copies of both lines, and the line that was already below");
  }

  /** Backspacing inside the session takes the character out of what gets repeated, not just out of the document. */
  @Test
  void aBackspaceInsideTheSessionRepeatsWhatSurvives() {
    cursorAt(MIDDLE_OF_LINE_TWO, 1, 1);

    press("3");
    press("g");
    type("abcd");
    press("Backspace");
    press("Escape");

    assertEquals("one\nabc\nabc\nabc\ntwo\n", emc.getEditorContent());
  }

  /**
   * Arrowing left and typing means the character typed is repeated and the character arrowed over is not — the reason
   * the session is tracked by appending and popping rather than read back out of the document.
   *
   * <p>The copies land at the caret, which is the rule the ticket gives ("inserted {@code count - 1} more times at the
   * caret"), and after arrowing left the caret is in the middle of what was typed. The session's text is what is
   * repeated either way: {@code acb} on the line, {@code abc} in each copy.
   */
  @Test
  void aCharacterTypedAfterArrowingLeftIsRepeatedAndTheTextArrowedOverIsNot() {
    cursorAt(MIDDLE_OF_LINE_TWO, 1, 1);

    press("3");
    press("g");
    type("ab");
    press("ArrowLeft");
    type("c");
    press("Escape");

    assertEquals("one\nac\nabc\nabcb\ntwo\n", emc.getEditorContent(),
        "the copies hold the text the session inserted, not the text it moved over");
  }

  /** No count, no repeat: the common session costs nothing and changes nothing. */
  @Test
  void gWithoutACountStillTypesOneLine() {
    cursorAt(MIDDLE_OF_LINE_TWO, 1, 1);

    press("g");
    type("abc");
    press("Escape");

    assertEquals("one\nabc\ntwo\n", emc.getEditorContent());
  }

  /**
   * The count is spent by the key that stashed it, so the next {@code g} is an ordinary one. It cannot leak back in
   * through the stash: the stash is what reads the count, not the other way round.
   *
   */
  @Test
  void aCountSpentOneSessionIsNotSpentAgainInTheNextOne() {
    cursorAt(MIDDLE_OF_LINE_TWO, 1, 1);
    press("3");
    press("g");
    type("abc");
    press("Escape");

    cursorAt(1, 1, 0); // the session left the caret at the end of the third copy; this test is about the next one
    press("g");
    type("x");
    press("Escape");

    assertEquals("x\none\nabc\nabc\nabc\ntwo\n", emc.getEditorContent(), "one x, however the first session repeated");
  }

  /**
   * Replace mode records nothing to repeat — it overwrites rather than inserts, so there is no session text to lay out
   * again. What this pins is the other half: the count spent on {@code o} is spent, and does not go on moving the caret
   * three columns per character the way it did before the count was retired at the key that took it.
   */
  @Test
  void aCountBeforeReplaceModeIsSpentByTheKeyAndNotByTheTyping() {
    emc.putEditorContent("one\ntwo\n");
    chooseCursorQuantum("character");
    cursorAt(4, 0, 1);

    press("3");
    press("o");
    type("abc");
    press("Escape");

    assertEquals("one\nabc\n", emc.getEditorContent(), "three characters over three characters, not three of each");
  }

  private void type(String text) {
    for (String key : text.split("")) {
      press(key);
    }
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
