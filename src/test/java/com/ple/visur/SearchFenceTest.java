package com.ple.visur;

import io.vertx.rxjava3.core.Vertx;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The scope quantum is the fence a <em>search</em> looks inside, and it is nothing else's ceiling.
 *
 * <p>It was drafted as a second job during this effort — a ceiling on {@code d} / {@code y} / {@code p}, growing
 * itself by promotion to hold whatever the count asked for. That job is struck: a fence that grows to hold the
 * extent it is supposed to cap never caps anything, and the operator would then be limited only by the end of the
 * content, which the bound functions already enforce. What the fence is for is answering "search this line, not
 * this file", which is the one thing it does and the first thing these tests pin.
 *
 * <p>The rest of the file is the other half of the same rule: selection and deletion do not consult it, so a
 * selection that runs past the fence keeps running. That is not a loophole — {@code s} shows the text, and an
 * operator that then worked on less than what was shown is an editor nobody trusts.
 */
class SearchFenceTest {
  private static final String CONTENT = "alpha needle beta gamma\nzeta needle eta\n";
  private static final int FIRST_NEEDLE = CONTENT.indexOf("needle"); // 6, on the first line
  private static final int SECOND_NEEDLE = CONTENT.indexOf("needle", FIRST_NEEDLE + 1); // past the line's fence

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
    emc.session().putCanvasWidth(80);
    emc.session().putCanvasHeight(24);
    emc.putCA(0);
    emc.putCX(0);
    emc.putCY(0);
    emc.session().putVirtualCX(0);
    emc.session().putCanvasStart(0);
    emc.session().putFY(0);
    emc.putCursorQuantum(emc.getQuantumNameToQuantum().get("character"));
    emc.putSpan(1);
    assertEquals(6, FIRST_NEEDLE);
    assertEquals(29, SECOND_NEEDLE);
  }

  @AfterEach
  void tearDown() {
    vertx.close();
  }

  @Test
  void aSearchInsideALineFenceDoesNotReachTheSecondLine() {
    fence("line");
    search("needle");
    assertEquals(FIRST_NEEDLE, emc.getCA());

    search("needle"); // the next needle is on the next line, which is outside the fence
    assertEquals(FIRST_NEEDLE, emc.getCA(), "a search that found nothing must leave the cursor where it was");
  }

  /** The control for the test above: it was the fence that stopped the search, not the search. */
  @Test
  void theSameSearchInsideADocumentFenceReachesTheSecondLine() {
    fence("document");
    search("needle");
    search("needle");
    assertEquals(SECOND_NEEDLE, emc.getCA());
  }

  /**
   * A character fence is the smallest fence there is, and it still does not clip a selection: five words selected
   * from inside "alpha" run to the end of "zeta" across the line break, because the range is computed by the
   * cursor quantum and the fence is not mentioned anywhere on that path.
   */
  @Test
  void aSelectionRunsAcrossAFenceItIsSmallerThan() {
    fence("character");
    emc.putCursorQuantum(emc.getQuantumNameToQuantum().get("word"));
    emc.putCA(2); // inside "alpha"
    select(5);
    assertEquals(0, emc.session().getCursorQuantumStart());
    assertEquals(29, emc.session().getCursorQuantumEnd()); // the end of "zeta", on the second line
    assertEquals("character", emc.getScopeQuantum().getName(), "selecting does not move the fence");
  }

  /** Deleting what was selected deletes all of it, even where the fence could not hold it. */
  @Test
  void aDeleteAcrossTheFenceGoesAcrossTheFence() {
    fence("line");
    emc.putCursorQuantum(emc.getQuantumNameToQuantum().get("word"));
    emc.putCA(2);
    select(5);
    new DeleteCursorQuantumOp().execute(null);
    assertEquals("needle eta\n", emc.getEditorContent());
    assertEquals("line", emc.getScopeQuantum().getName(), "an operator does not move the fence");
  }

  /** The keystroke {@code f} + target + {@code Enter}: the submode leaves the target on the stack and runs the op. */
  private void search(String target) {
    emc.putOnExecutionDataStack(target);
    new SearchForwardOp().execute(null);
  }

  /** The keystroke {@code 5 s}: an operand on the stack outranks the count. */
  private void select(int span) {
    emc.putOnExecutionDataStack(span);
    new SetSpanOp().execute(null);
  }

  /** The keystroke {@code /} + a quantum key, which is the only thing that has ever written the fence. */
  private void fence(String quantumName) {
    emc.putScopeQuantum(emc.getQuantumNameToQuantum().get(quantumName));
  }
}
