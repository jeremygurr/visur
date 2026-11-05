package com.ple.visur;

import io.vertx.rxjava3.core.Vertx;
import io.vertx.core.json.JsonObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code g} opens a line above and {@code G} below, at the bounds of the enclosing line whatever the cursor quantum,
 * and drops you into insert mode on the line that just appeared.
 *
 * <p>Keypress-level, because the interesting thing is where the caret ends up after a command that changes both the
 * content and the mode: the line indices, the cursor's row, and the quantum all have to agree afterwards, and each of
 * those is a separate keypress-time read.
 *
 * <p>"In insert mode" is asserted on the {@link View} payload rather than the model field, since the mode line and both
 * clients read the wire.
 */
class OpenLineTest {
  /** Line starts: 0, 4, 8 (the phantom line after the trailing newline). */
  private static final String TWO_LINES = "one\ntwo\n";
  private static final int MIDDLE_OF_LINE_TWO = 5; // the "w" of "two"
  /** What both keys produce from {@link #TWO_LINES}: {@code g} from line two, {@code G} from line one. */
  private static final String WITH_A_LINE_OPENED = "one\n\ntwo\n";

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
    cursorAt(0, 0, 0);
  }

  @AfterEach
  void tearDown() {
    vertx.close();
  }

  @Test
  void gOpensALineAboveAndDropsYouIntoInsertModeOnIt() {
    chooseCursorQuantum("line");
    cursorAt(MIDDLE_OF_LINE_TWO, 1, 1);

    press("g");
    assertEquals(WITH_A_LINE_OPENED, emc.getEditorContent());
    assertEquals(4, emc.getCA(), "the caret is on the new empty line, not on either neighbour");
    assertEquals("insert", drawn().getString("editorSubmode"));
  }

  @Test
  void capitalGOpensALineBelowAndDropsYouIntoInsertModeOnIt() {
    chooseCursorQuantum("line");
    cursorAt(1, 1, 0); // line one

    press("G");
    assertEquals(WITH_A_LINE_OPENED, emc.getEditorContent());
    assertEquals(4, emc.getCA(), "the caret is on the new empty line, not on either neighbour");
    assertEquals("insert", drawn().getString("editorSubmode"));
  }

  /**
   * The caret is asserted as "what you type lands on the new line", because on a document with no trailing newline the
   * new empty line is one past the last character, and an index there only means anything by what it accepts.
   */
  @Test
  void capitalGOnTheLastLineWithNoTrailingNewlineStillGivesANewEmptyLine() {
    emc.putEditorContent("one\ntwo");
    chooseCursorQuantum("line");
    cursorAt(6, 2, 1); // the "o" that ends the document

    press("G");
    assertEquals("one\ntwo\n", emc.getEditorContent());
    press("x");
    assertEquals("one\ntwo\nx", emc.getEditorContent(),
        "the typed character goes on the new line, not at the end of the old one");
  }

  /**
   * An empty document has one line and it is empty, so {@code g} opens above it and {@code G} below it, and both leave
   * something to type on. The caret differs: {@code g} stays on the line that was already there, while {@code G}'s
   * newline terminates it and leaves the caret past that. Typing one character shows which.
   */
  @Test
  void gOnAnEmptyDocumentLeavesALineToTypeOn() {
    openFromNothing("g");
    assertEquals("x\n", emc.getEditorContent());
  }

  @Test
  void capitalGOnAnEmptyDocumentLeavesALineToTypeOn() {
    openFromNothing("G");
    assertEquals("\nx", emc.getEditorContent());
  }

  /**
   * The toggle changes where a line's selection ends, and {@code G} reads the line indices instead, so the document it
   * produces cannot depend on the toggle. Run twice in one test because the comparison is the point.
   */
  @Test
  void capitalGGivesTheSameDocumentWhicheverWayTheSeparatorToggleIsSet() {
    assertEquals(WITH_A_LINE_OPENED, openBelow(), "SEP=ON");

    press("\\"); // one backslash, which is what both clients actually send
    assertEquals(false, drawn().getBoolean("includeSeparators"), "the toggle actually moved, or this proves nothing");
    assertEquals(WITH_A_LINE_OPENED, openBelow(), "SEP=OFF");
  }

  /**
   * The test that pins "line-based whatever the quantum": at the word quantum the enclosing word starts mid-line, and
   * opening a line there would split the line instead of opening one above it.
   */
  @Test
  void gAtTheWordQuantumMidLineOpensALineAboveRatherThanSplittingTheLine() {
    emc.putEditorContent("one\ntwo three\n");
    chooseCursorQuantum("word");
    cursorAt(9, 5, 1); // the "h" of "three", whose word starts at 8 and whose line starts at 4

    press("g");
    assertEquals("one\n\ntwo three\n", emc.getEditorContent(),
        "the newline went at the line's start, not the word's");
    assertEquals(4, emc.getCA());
  }

  /**
   * Escape hands back what {@code g} stashed: the span and the cursor quantum. The range itself is re-derived at that
   * span from wherever the typing left the caret, which is Escape's existing behavior for {@code i} and {@code a}, so
   * what a selection needs to survive is a unit under the caret — here, three characters of the line below.
   */
  @Test
  void escapeRestoresTheSpanAndQuantumThatWereStashedOnEntry() {
    chooseCursorQuantum("character");
    press("3");
    press("s");
    cursorAt(MIDDLE_OF_LINE_TWO, 1, 1);

    press("g");
    assertEquals(0, drawn().getInteger("span"), "the session runs at span 0, like i and a");
    assertEquals("character", drawn().getString("cursorQuantum"));
    press("h");
    press("i");
    press("Escape");

    JsonObject drawn = drawn();
    assertEquals(3, drawn.getInteger("span"), "the three the user selected before the key");
    assertEquals("character", drawn.getString("cursorQuantum"));
    assertTrue(emc.getCursorQuantumEnd() > emc.getCursorQuantumStart(), "a selection survived the round trip");
  }

  /**
   * {@code 3 g} does not open three lines. It opens one and keeps the count for the session, which
   * [05](../../wayfinder/separators-and-undo/issues/05-insert-repeat.md) spends on the typed text. The count has to be
   * copied out of the model on the way in: the keypress that opens the session is also the one that retires it.
   */
  @Test
  void threeGOpensOneLineAndStashesTheCountForTheSession() {
    chooseCursorQuantum("line");
    cursorAt(MIDDLE_OF_LINE_TWO, 1, 1);

    press("3");
    press("g");
    assertEquals(WITH_A_LINE_OPENED, emc.getEditorContent(), "one newline, however big the count");
    assertEquals(3, stashedCount(), "the count, kept for the session that is about to be typed");
  }

  /**
   * The other half of the stash: a session opened with no count clears what the last one left, or {@code 3 g} would
   * repeat text this session never typed.
   */
  @Test
  void gWithoutACountLeavesNoCountForTheSession() {
    chooseCursorQuantum("line");
    cursorAt(MIDDLE_OF_LINE_TWO, 1, 1);
    press("3");
    press("g");
    press("Escape");

    press("g");
    assertNull(stashedCount(), "the stashed three did not survive into the session that typed no count");
  }

  /**
   * A fresh two-line document, {@code G} on line one, and the document it left — back in navigate mode afterwards,
   * because in insert mode the caller's next keypress is a character rather than a command.
   */
  private String openBelow() {
    emc.putEditorContent(TWO_LINES);
    emc.putSpan(1);
    chooseCursorQuantum("line");
    cursorAt(1, 1, 0);
    press("G");
    String opened = emc.getEditorContent();
    press("Escape"); // leave insert mode, so the caller's next keypress is a command and not a character
    return opened;
  }

  /** The empty document, the key pressed, and one character typed: what a user would see. */
  private void openFromNothing(String key) {
    emc.putEditorContent("");
    cursorAt(0, 0, 0);
    chooseCursorQuantum("line");
    press(key);
    assertEquals("\n", emc.getEditorContent(), key + " on nothing still gives a line to type on");
    press("x");
  }

  private JsonObject drawn() {
    return View.of(emc).render();
  }

  /** What {@code g} stashed for the session: the count typed before it, or nothing. */
  private Integer stashedCount() {
    VisurVar stashed = emc.getGlobalVar("tempCount");
    return stashed == null ? null : (Integer) stashed.getVal();
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
