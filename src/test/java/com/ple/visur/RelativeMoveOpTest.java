package com.ple.visur;

import io.vertx.rxjava3.core.Vertx;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A move re-derives the selection; it does not revise the span.
 *
 * <p>A line with no word in it selects nothing at the word quantum, and used to take the span down with it. Span zero
 * is not recoverable by moving — every range derived from span zero is empty — so one empty line was a trap door:
 * the span could not come back. The number belongs to {@code s}, so the only thing that writes it.
 */
class RelativeMoveOpTest {
  private static final String CONTENT = "one two\n\nthree four\n";

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
    emc.putCursorQuantum(emc.getQuantumNameToQuantum().get("word"));
    emc.putCA(0);
    emc.putCX(0);
    emc.putCY(0);
    emc.putVirtualCX(0);
    emc.putCanvasStart(0);
    emc.putFY(0);
    emc.putSpan(1);
  }

  @AfterEach
  void tearDown() {
    vertx.close();
  }

  @Test
  void aLineWithNoWordInItKeepsTheSpanAndSelectsNothing() {
    move(0, 1); // down from "one" onto the empty line
    assertEquals(8, emc.getCA());
    assertEquals(1, emc.getSpan());
    assertEquals(0, selectedWidth());
  }

  @Test
  void movingBackOntoAWordSelectsItAgain() {
    move(0, 1);
    move(0, 1); // and onto "three"
    assertEquals(1, emc.getSpan());
    assertEquals(9, emc.getCursorQuantumStart());
    assertEquals(15, emc.getCursorQuantumEnd());
  }

  /**
   * Word movement shares {@code getNextBound} with the selection, so this pins the walk from the other side: the
   * branch that returns the end of the quantum under the cursor is now {@code quantumEnd}, and a movement test is what
   * says it means the same thing.
   */
  @Test
  void aWideGapIsOneStepInBothDirections() {
    emc.putEditorContent("one   two   three"); // words at 0, 6 and 12
    move(1, 0);
    assertEquals(6, emc.getCA(), "right, onto the next word");
    move(-1, 0);
    assertEquals(0, emc.getCA(), "and back");
  }

  private int selectedWidth() {
    return emc.getCursorQuantumEnd() - emc.getCursorQuantumStart();
  }

  private void move(int dx, int dy) {
    emc.putOnExecutionDataStack(dx); // the operator pops dy, then dx
    emc.putOnExecutionDataStack(dy);
    new RelativeMoveOp().execute(null);
  }
}
