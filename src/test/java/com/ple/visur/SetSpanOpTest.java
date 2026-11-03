package com.ple.visur;

import io.vertx.rxjava3.core.Vertx;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Span is the number the key asked for, not a flag for "something is selected".
 *
 * <p>Booted the way a session boots ([{@link InitializerService#initializeEditorModel()}]) because the thing under
 * test is what the operator leaves in the model, and the model is shared state behind the coupler. The document is
 * replaced with something short enough to state a range in full.
 */
class SetSpanOpTest {
  private static final String CONTENT = "one two three four five\n";

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
    emc.putCA(0);
    emc.putCX(0);
    emc.putCY(0);
    emc.putVirtualCX(0);
    emc.putCanvasStart(0);
    emc.putFY(0);
    emc.putCursorQuantum(new CharacterQuantum());
    emc.putSpan(1);
  }

  @AfterEach
  void tearDown() {
    vertx.close();
  }

  private void requestSpan(int span) {
    emc.putOnExecutionDataStack(span);
    new SetSpanOp().execute(null);
  }

  private int selectedWidth() {
    return emc.getCursorQuantumEnd() - emc.getCursorQuantumStart();
  }

  @Test
  void theSpanKeepsTheNumberItWasGiven() {
    requestSpan(7);
    assertEquals(7, emc.getSpan());
    assertEquals(0, emc.getCursorQuantumStart());
    assertEquals(7, emc.getCursorQuantumEnd());
  }

  @Test
  void aSpanOfZeroCollapsesTheRange() {
    requestSpan(3);
    requestSpan(0);
    assertEquals(0, emc.getSpan());
    assertEquals(0, selectedWidth());
  }

  @Test
  void aSpanWiderThanTheContentKeepsTheRequestedSpanAndSelectsWhatIsLeft() {
    emc.putEditorContent("abc\n");
    requestSpan(10);
    assertEquals(10, emc.getSpan());
    assertEquals(0, emc.getCursorQuantumStart());
    assertEquals(4, emc.getCursorQuantumEnd());
  }

  @Test
  void nothingToSelectLeavesNoSpan() {
    emc.putCA(CONTENT.length()); // at the end of the content there is nothing left to select
    requestSpan(3);
    assertEquals(0, emc.getSpan());
    assertEquals(0, selectedWidth());
  }

  @Test
  void deletingLeavesAWindowThatWideBehind() {
    requestSpan(3);
    new DeleteCursorQuantumOp().execute(null);
    assertEquals(3, emc.getSpan());
    assertEquals(3, selectedWidth());
  }

  @Test
  void movingAfterADeleteRederivesAWindowThatWide() {
    requestSpan(3);
    new DeleteCursorQuantumOp().execute(null);
    emc.putOnExecutionDataStack(1);
    emc.putOnExecutionDataStack(0);
    new RelativeMoveOp().execute(null);
    assertEquals(3, emc.getSpan());
    assertEquals(3, selectedWidth());
  }

  @Test
  void deletingTheLastSelectableTextLeavesNoSpan() {
    emc.putEditorContent("ab\n");
    emc.putCA(0);
    requestSpan(3); // wider than the content, so the whole thing is selected
    new DeleteCursorQuantumOp().execute(null);
    assertEquals(0, emc.getSpan());
    assertEquals(0, selectedWidth());
  }
}
