package com.ple.visur;

import io.vertx.rxjava3.core.Vertx;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

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
  private static final String TWENTY_FIVE_WORDS = IntStream.rangeClosed(1, 25)
      .mapToObj(word -> "w" + word)
      .collect(Collectors.joining(" ")) + "\n";

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
    emc.session().putCA(0);
    emc.session().putCX(0);
    emc.session().putCY(0);
    emc.session().putVirtualCX(0);
    emc.session().putCanvasStart(0);
    emc.session().putFY(0);
    emc.session().putCursorQuantum(new CharacterQuantum());
    emc.session().putSpan(1);
  }

  @AfterEach
  void tearDown() {
    vertx.close();
  }

  private void requestSpan(int span) {
    emc.session().putOnExecutionDataStack(span);
    new SetSpanOp().execute(null);
  }

  /** How many words the selection covers, counted from what is actually selected. */
  private int wordsSelected() {
    String selected = emc.document().getContent().substring(emc.session().getCursorQuantumStart(), emc.session().getCursorQuantumEnd()).trim();
    return selected.isEmpty() ? 0 : selected.split("\\s+").length;
  }

  private int selectedWidth() {
    return emc.session().getCursorQuantumEnd() - emc.session().getCursorQuantumStart();
  }

  @Test
  void theSpanKeepsTheNumberItWasGiven() {
    requestSpan(7);
    assertEquals(7, emc.session().getSpan());
    assertEquals(0, emc.session().getCursorQuantumStart());
    assertEquals(7, emc.session().getCursorQuantumEnd());
  }

  @Test
  void aSpanOfZeroCollapsesTheRange() {
    requestSpan(3);
    requestSpan(0);
    assertEquals(0, emc.session().getSpan());
    assertEquals(0, selectedWidth());
  }

  @Test
  void aSpanWiderThanTheContentKeepsTheRequestedSpanAndSelectsWhatIsLeft() {
    emc.document().putContent("abc\n");
    requestSpan(10);
    assertEquals(10, emc.session().getSpan());
    assertEquals(0, emc.session().getCursorQuantumStart());
    assertEquals(4, emc.session().getCursorQuantumEnd());
  }

  @Test
  void nothingLeftToSelectKeepsTheNumberAndSelectsNothing() {
    // the span is the number the key was given, the range is what the content allows. Zeroing the span here used to
    // look harmless and was a trap door: a move derives its range from the span, so span zero derives nothing.
    emc.session().putCA(CONTENT.length()); // at the end of the content there is nothing left to select
    requestSpan(3);
    assertEquals(3, emc.session().getSpan());
    assertEquals(0, selectedWidth());
  }

  @Test
  void thePendingCountBecomesTheSpan() {
    emc.session().putPendingCount(12);
    new SetSpanOp().execute(null);
    assertEquals(12, emc.session().getSpan());
    assertEquals(12, selectedWidth());
  }

  @Test
  void noPendingCountMeansOneQuantum() {
    new SetSpanOp().execute(null);
    assertEquals(1, emc.session().getSpan());
  }

  @Test
  void aPendingCountOfZeroCollapsesTheSelection() {
    requestSpan(3);
    emc.session().putPendingCount(0);
    new SetSpanOp().execute(null);
    assertEquals(0, emc.session().getSpan());
    assertEquals(0, selectedWidth());
  }

  @Test
  void abindingThatNamesTheSpanIgnoresThePendingCount() {
    // i, a and o enter insert mode with "0 setSpan" compiled into the binding, which outranks a stray count
    emc.session().putPendingCount(5);
    emc.session().putOnExecutionDataStack(0);
    new SetSpanOp().execute(null);
    assertEquals(0, emc.session().getSpan());
  }

  /**
   * Reversed by paste ticket 03: the delete used to re-derive a window over whatever slid into the hole, which is a
   * highlight over text nobody selected — and {@code p} replaces the selection, so {@code d} then {@code p} ate that
   * text instead of putting the deleted text back. The span survives; the range is the gap.
   */
  @Test
  void deletingLeavesAGapAndKeepsTheSpanForTheNextMove() {
    requestSpan(3);
    new DeleteCursorQuantumOp().execute(null);
    assertEquals(3, emc.session().getSpan());
    assertEquals(0, selectedWidth(), "the highlight marks the gap the delete made");
  }

  @Test
  void movingAfterADeleteRederivesAWindowThatWide() {
    requestSpan(3);
    new DeleteCursorQuantumOp().execute(null);
    emc.session().putOnExecutionDataStack(1);
    emc.session().putOnExecutionDataStack(0);
    new RelativeMoveOp().execute(null);
    assertEquals(3, emc.session().getSpan());
    assertEquals(3, selectedWidth());
  }

  /**
   * The range is a function of the span, and running out of content does not reduce the span, so walking back into the
   * document grows the window back to the number that was asked for and holds it there. A stored span that had been
   * clamped to what was available would leave this stuck at one word.
   */
  @Test
  void aWindowThatRanOutOfContentGrowsBackToTheSizeThatWasAskedFor() {
    emc.session().putCursorQuantum(emc.getQuantumNameToQuantum().get("word"));
    emc.document().putContent(TWENTY_FIVE_WORDS);
    emc.session().putCA(TWENTY_FIVE_WORDS.lastIndexOf(' ') + 1); // the last word, with one word left to select
    requestSpan(5);
    emc.session().clearPendingCount(); // the keypress path retires the count once spent; this test drives the operators directly
    assertEquals(5, emc.session().getSpan());

    List<Integer> wordsAtEachStep = new ArrayList<>();
    for (int step = 0; step < 5; step++) {
      emc.session().putOnExecutionDataStack(-1); // one word back; the operator pops dy, then dx
      emc.session().putOnExecutionDataStack(0);
      new RelativeMoveOp().execute(null);
      wordsAtEachStep.add(wordsSelected());
    }

    assertEquals(List.of(2, 3, 4, 5, 5), wordsAtEachStep, "the window grows back to the five asked for, then holds");
  }

  @Test
  void deletingTheLastTextKeepsTheSpanAndSelectsNothing() {
    emc.document().putContent("ab\n");
    emc.session().putCA(0);
    requestSpan(3); // wider than the content, so the whole thing is selected
    new DeleteCursorQuantumOp().execute(null);
    assertEquals(0, selectedWidth());
    assertEquals(3, emc.session().getSpan(), "delete takes the span down no further than a move does");
  }
}
