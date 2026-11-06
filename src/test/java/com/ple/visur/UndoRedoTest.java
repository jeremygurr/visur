package com.ple.visur;

import io.vertx.rxjava3.core.Vertx;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@code u} and {@code R}: a ring of snapshots, taken before a command changes content, walked back and forward one
 * command at a time.
 *
 * <p>Keypress-level throughout, like the tickets this map built on. The unit under test is a command — one keypress in
 * navigate mode, or a whole insert session closed by Escape — and nothing below that boundary is worth pinning: the
 * snapshot is taken at one choke point so no operator can get its own boundary wrong.
 */
class UndoRedoTest {
  /** Line starts: 0, 4, 8. */
  private static final String TWO_LINES = "one\ntwo\n";
  private static final int MIDDLE_OF_LINE_TWO = 5; // the "w" of "two"

  @TempDir
  Path tempDir;

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

  /** The first thing anyone does with undo: put back the text a {@code d} removed. */
  @Test
  void undoBringsBackWhatTheDeleteRemoved() {
    press("d");
    assertEquals("two\n", emc.getEditorContent(), "the delete happened first");

    press("u");

    assertEquals(TWO_LINES, emc.getEditorContent());
  }

  /**
   * The selection comes back too, so the command that deleted is one keypress away again rather than a selection to
   * rebuild. Movement is not an edit, so the snapshot the {@code d} pushed is the state after the {@code j}.
   */
  @Test
  void undoPutsBackTheSpanAndCursorSoTheCommandCanRunAgain() {
    press("j"); // onto line two; not an edit, so nothing is snapshot for it
    press("d");
    assertEquals("one\n", emc.getEditorContent());

    press("u");

    assertEquals(TWO_LINES, emc.getEditorContent());
    assertEquals(MIDDLE_OF_LINE_TWO - 1, emc.getCursorQuantumStart(), "line two selected again");
    assertEquals(8, emc.getCursorQuantumEnd());
    assertEquals(1, emc.getSpan());
    assertEquals(4, emc.getCA());

    press("d");
    assertEquals("one\n", emc.getEditorContent(), "one keypress, not a selection to rebuild");
  }

  /**
   * The unit is a command and the boundary is entering navigate mode, so the whole session — the newline {@code g}
   * opened, every character typed — comes back in one {@code u}. Undo that removed the words and left the blank line
   * behind is the bug the design exists to avoid.
   */
  @Test
  void anInsertSessionIsOneUndo() {
    cursorAt(MIDDLE_OF_LINE_TWO, 1, 1);
    press("g");
    type("hello there");
    press("Escape");
    assertEquals("one\nhello there\ntwo\n", emc.getEditorContent(), "the session happened");

    press("u");

    assertEquals(TWO_LINES, emc.getEditorContent(), "the words and the line they were typed on, together");
  }

  /** A count multiplies what one command removes, and it is still one command: one {@code u} brings all of it back. */
  @Test
  void aCountedDeleteIsOneUndo() {
    emc.putEditorContent("one\ntwo\nthree\nfour\n");

    press("3");
    press("d");
    assertEquals("four\n", emc.getEditorContent());

    press("u");

    assertEquals("one\ntwo\nthree\nfour\n", emc.getEditorContent());
  }

  /** The bottom of the undo stack and the top of the redo stack are both no-ops, and neither breaks the next undo. */
  @Test
  void undoAtTheBottomAndRedoAtTheTopDoNothing() {
    press("u"); // nothing has been edited yet
    assertEquals(TWO_LINES, emc.getEditorContent());
    press("R"); // nothing has been undone

    assertEquals(TWO_LINES, emc.getEditorContent());

    press("d");
    press("u");
    assertEquals(TWO_LINES, emc.getEditorContent(), "the stack still had the delete in it");
  }

  /** A new edit clears the redo stack: what {@code R} was offering no longer describes the document. */
  @Test
  void aNewEditClearsTheRedoStack() {
    press("d");
    press("u");

    press("g"); // a new edit, however small
    press("Escape");
    assertEquals("\none\ntwo\n", emc.getEditorContent());

    press("R");
    assertEquals("\none\ntwo\n", emc.getEditorContent(), "the redo that was cleared is not offered again");
  }

  /**
   * The accepted asymmetry, and the test that argues against "fixing" it: undoing a paste puts the document back and
   * leaves the register holding the text that paste displaced. A register restored from a snapshot can name text that
   * is no longer in the document.
   */
  @Test
  void undoLeavesTheRegisterAlone() {
    press("y"); // yank line one
    press("j");
    press("p");
    assertEquals("one\none\n", emc.getEditorContent());
    assertEquals("two\n", register(), "the paste displaced line two into the register");

    press("u");

    assertEquals(TWO_LINES, emc.getEditorContent());
    assertEquals("two\n", register(), "the register keeps what the paste put there");
  }

  /**
   * Snapshotting a document and then loading another would leave {@code u} pasting one file's text into another's, so a
   * load empties both stacks. A safe default is only safe if it is a decision.
   */
  @Test
  void loadingAFileEmptiesBothStacks() throws IOException {
    Path file = tempDir.resolve("next.txt");
    Files.write(file, "loaded\n".getBytes(StandardCharsets.UTF_8));
    press("d");
    assertEquals("two\n", emc.getEditorContent(), "an edit, so there is something to undo");

    emc.putOnExecutionDataStack(file.toString());
    new LoadFileOp().execute(null);

    press("u");
    assertEquals("loaded\n", emc.getEditorContent(), "the delete belonged to the document before this one");
  }

  /**
   * A pasted block is a command like any other, so one {@code u} takes it back. Bracketed paste does not arrive through
   * a keypress, which is why it needs the same bracket rather than inheriting it.
   */
  @Test
  void aPastedBlockIsOneUndo() {
    KeyWasPressedVerticle.pasteText(emc, "pasted\n");
    assertEquals("pasted\none\ntwo\n", emc.getEditorContent());

    press("u");

    assertEquals(TWO_LINES, emc.getEditorContent());
  }

  private String register() {
    return (String) emc.session().getGlobalVar("yankRegister").getVal();
  }

  private void type(String text) {
    for(String ch : text.split("")) {
      press(ch);
    }
  }

  private void chooseCursorQuantum(String name) {
    emc.putCursorQuantum(emc.getQuantumNameToQuantum().get(name));
  }

  private void cursorAt(int ca, int cx, int cy) {
    emc.putCX(cx);
    emc.putCY(cy);
    emc.putVirtualCX(cx);
    emc.putFY(cy);
    emc.putCA(ca);
  }

  private void press(String key) {
    KeyWasPressedVerticle.press(emc, KeyPressed.from(key), key);
    CountPrefix.retireUnlessDigit(emc, key);
  }
}
