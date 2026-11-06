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

  @Test
  void choosingAQuantumWithNoUnitHereKeepsTheSpanAndSelectsNothing() {
    emc.session().putOnExecutionDataStack("word");
    new ChangeCursorQuantumOp().execute(null);
    assertEquals("word", emc.session().getCursorQuantum().getName());
    assertEquals(1, emc.session().getSpan());
    assertEquals(0, emc.session().getCursorQuantumEnd() - emc.session().getCursorQuantumStart());
  }
}
