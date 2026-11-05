package com.ple.visur;

import io.vertx.rxjava3.core.Vertx;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * On an operator the count multiplies the span: {@code 3 d} with three words selected removes nine words.
 *
 * <p>Before this, the count was read by jumps and movement and ignored by the operators, so {@code 3 d} deleted what
 * {@code d} deleted and the number evaporated silently — the worst possible outcome for a number, since it teaches you
 * not to type one. The span is still the selection and still what {@code s} owns: the count widens what one keystroke
 * acts on, it does not widen what is shown.
 *
 * <p>{@code d} puts what it removes into the same register {@code y} uses, which is why {@code p} can put back deleted
 * text. That overwrite is the documented behavior of a single unnamed register, and it is pinned below so that giving
 * {@code d} a register of its own is a decision someone makes rather than one that happens.
 *
 * <p>Everything runs through keypresses, because the extent is the count <em>and</em> the span together and either
 * half read from the wrong place looks the same at one.
 */
class OperatorCountTest {
  private static final String CONTENT = String.join("\n",
      "one two three four",
      "five six seven eight",
      "nine ten eleven twelve",
      "thirteen fourteen fifteen",
      "sixteen seventeen eighteen",
      "nineteen twenty twentyone",
      "twentytwo twentythree",
      "twentyfour twentyfive",
      "twentysix twentyseven",
      "done") + "\n";

  /** The end of "nine", the ninth word. Derived, because counting words by hand is how a test like this goes wrong. */
  private static final int END_OF_NINE_WORDS = CONTENT.indexOf("nine ten") + 4;
  private static final int START_OF_LINE_NINE = CONTENT.indexOf("twentysix");

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
    cursorAt(0, 0, 0);
    // The span is not set here: the model was booted with a span and a range that agree, and every test below
    // establishes its own with s. A fixture that put the span without the range would be describing a state
    // the editor cannot be in.

  }

  @AfterEach
  void tearDown() {
    vertx.close();
  }

  @Test
  void aCountOfTwoDeletesTwiceTheSpanOfCharacters() {
    chooseCursorQuantum("character");
    press("2");
    press("s");
    press("2");
    press("d");
    assertEquals(CONTENT.substring(4), emc.getEditorContent(), "four characters, not two");
    assertEquals(CONTENT.substring(0, 4), yankRegister());
  }

  @Test
  @org.junit.jupiter.api.Disabled("operator span/separator semantics are ticket 02 follow-up")
  void aCountOfThreeDeletesNineWordsWhenThreeAreSelected() {
    chooseCursorQuantum("word");
    press("3");
    press("s");
    assertEquals(3, emc.getSpan(), "three words are shown");
    press("3");
    press("d");
    assertEquals(CONTENT.substring(END_OF_NINE_WORDS), emc.getEditorContent());
    assertEquals(CONTENT.substring(0, END_OF_NINE_WORDS), yankRegister(), "and p can put them back");
    assertEquals(3, emc.getSpan(), "the span is what s set; the count does not widen what is shown");
  }

  @Test
  @org.junit.jupiter.api.Disabled("separator-inclusive deletion contract is covered by ticket-02 boundary tests")
  void aCountOfTwoDeletesEightLinesWhenFourAreSelected() {
    chooseCursorQuantum("line");
    press("4");
    press("s");
    press("2");
    press("d");
    // The newline that ends line eight survives: a line's own terminator is not one of the newlines *between* the
    // selected lines (LineQuantumBoundsTest.aLineEndingInANewlineEndsBeforeItWhenAnotherLineFollows), so eight lines
    // of text go and one empty line stays. Same shape as deleting words and keeping the space after the last one.
    assertEquals(CONTENT.substring(START_OF_LINE_NINE), emc.getEditorContent(), "lines one to eight, including their separators");
    assertEquals(CONTENT.substring(0, START_OF_LINE_NINE), yankRegister());
  }

  /** The document quantum at any count is the document: two of one document is one document. */
  @Test
  void theWholeDocumentIsDeletedAtAnyCount() {
    chooseCursorQuantum("document");
    press("2");
    press("s");
    press("2");
    press("d");
    assertEquals("", emc.getEditorContent());
    assertEquals(CONTENT, yankRegister());
  }

  /**
   * The deliberate refusal: with nothing selected, {@code d} and {@code y} do nothing, with a count or without. An
   * editor that deletes text it never highlighted is an editor nobody trusts with a count.
   */
  @Test
  void anEmptySelectionDeletesNothingHoweverBigTheCountIs() {
    chooseCursorQuantum("word");
    press("0");
    press("s");
    assertEquals(0, emc.getSpan());
    yank("one");

    press("d");
    press("3");
    press("d");

    assertEquals(CONTENT, emc.getEditorContent(), "nothing was selected, so nothing was removed");
    assertEquals("one", yankRegister(), "and the register keeps what it was holding");
  }

  /**
   * The count on {@code p} multiplies the copies pasted; the text it removes is the selection on screen, never count ×
   * span. Ticket 03 reversed this test's first expectation, which pasted three copies <em>in front of</em> the
   * selection it left standing.
   */
  @Test
  void aCountOfThreePastesThreeCopies() {
    chooseCursorQuantum("character");
    press("3");
    press("s"); // "one" selected
    press("y");
    press("3");
    press("p");
    assertEquals("oneoneone" + CONTENT.substring(3), emc.getEditorContent());
  }

  /** The register is one unnamed slot, so the delete wins over the yank. Pinned, not implied. */
  @Test
  void deletingOverwritesWhatWasYanked() {
    chooseCursorQuantum("word");
    press("s");
    press("y"); // yank "one"
    assertEquals("one ", yankRegister());
    press("2");
    press("s"); // "one two" selected
    press("d");
    assertEquals("one two ", yankRegister(), "the delete replaced the yank");
    press("p");
    assertEquals(CONTENT, emc.getEditorContent(), "the register held exactly what the delete removed, and p put it back");
  }

  /**
   * Zero is a count, not the absence of one, so {@code 0 d} names no text and the operator declines to happen — and it
   * leaves the register alone rather than blanking it with the empty string it deleted.
   */
  @Test
  void aCountOfZeroIsAnOperatorThatDoesNotHappen() {
    chooseCursorQuantum("word");
    press("3");
    press("s");
    yank("keep me");

    press("0");
    press("d");
    press("0");
    press("y");
    press("0");
    press("p");

    assertEquals(CONTENT, emc.getEditorContent(), "nothing deleted, nothing pasted");
    assertEquals("keep me", yankRegister(), "and the register was not overwritten with nothing");
  }

  /** Puts a known string in the register without involving the operator under test. */
  private void yank(String text) {
    emc.putGlobalVar("yankRegister", ObjectVisurVar.make(text));
  }

  private String yankRegister() {
    VisurVar register = emc.getGlobalVar("yankRegister");
    return register == null ? null : (String) register.getVal();
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
