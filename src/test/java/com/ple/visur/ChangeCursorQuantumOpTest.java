package com.ple.visur;

import io.vertx.rxjava3.core.Vertx;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Choosing a quantum re-derives the selection; it does not revise the span.
 *
 * <p>Same rule as {@link RelativeMoveOpTest}, same trap door: {@code w} where the cursor sits on whitespace has no
 * word to select, and used to take the span to zero on the way out.
 */
class ChangeCursorQuantumOpTest {
  private static final String CONTENT = "one two\n\nthree four\n";
  private static final int EMPTY_LINE = 8; // the blank line's own line break, with no word on it

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
    emc.session().putCursorQuantum(new CharacterQuantum());
    emc.session().putCA(EMPTY_LINE);
    emc.session().putCX(0);
    emc.session().putCY(1);
    emc.session().putVirtualCX(0);
    emc.session().putCanvasStart(0);
    emc.session().putFY(0);
    emc.session().putSpan(1);
  }

  @AfterEach
  void tearDown() {
    vertx.close();
  }

  /**
   * The word is this command's argument, so the op has to take it whichever way the lookup goes — including when the
   * word names a session variable that holds a quantum, which is how Escape restores what an insert session
   * interrupted. That branch used to <em>peek</em>. The word stayed behind on the command stack, and the next command
   * to ask for an argument got {@code tempCursorQuantum} instead: {@code ZZ} reads its path from that stack, so
   * <strong>saving after typing had never worked</strong> — it tried to write a file named after the cursor quantum,
   * failed on a null parent, and died in the verticle without a message or a repaint.
   */
  @Test
  void aQuantumNamedByASessionVariableIsConsumedToo() {
    emc.session().putGlobalVar("tempCursorQuantum", ObjectVisurVar.make(emc.getQuantumNameToQuantum().get("word")));
    emc.session().putOnExecutionDataStack("tempCursorQuantum");

    new ChangeCursorQuantumOp().execute(null);

    assertEquals("word", emc.session().getCursorQuantum().getName(), "the variable names the quantum, so the variable is used");
    assertEquals(0, emc.session().getExecutionDataStack().size(),
      "and the word is off the stack: it was this command's argument, not the next command's");
  }

  @Test
  void choosingAQuantumWithNoUnitHereKeepsTheSpanAndSelectsNothing() {
    emc.session().putOnExecutionDataStack("word");
    new ChangeCursorQuantumOp().execute(null);
    assertEquals("word", emc.session().getCursorQuantum().getName());
    assertEquals(1, emc.session().getSpan());
    assertEquals(0, emc.session().getCursorQuantumEnd() - emc.session().getCursorQuantumStart());
  }
}
