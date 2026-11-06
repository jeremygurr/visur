package com.ple.visur;

public class SetSpanOp implements Operator {
  @Override
  public void execute(Object opInfo) {
    EditorModelCoupler emc = ServiceHolder.editorModelCoupler;
    int spanToSet = spanRequested(emc);
    int[] bounds = emc.getCursorQuantum().getBoundaries(emc.getCA(), emc.getNextLineIndices(), spanToSet, false);
    emc.session().putCursorQuantumStartAndScroll(bounds[0]);
    emc.session().putCursorQuantumEndAndScroll(bounds[1]);
    // The number the key was given, kept even where the range clamped short of it or collapsed entirely. Nothing else
    // writes the span: a move derives its range from the span, so a span of zero derives nothing, and a selection
    // that fell to zero because there was no unit of the quantum here could never come back.
    emc.putSpan(spanToSet);
  }

  /**
   * Where the span came from: an operand, when a compiled binding names one itself ({@code 0 setSpan} is how {@code i},
   * {@code a} and {@code o} enter insert mode); then the count typed before the key; then one, which is what a bare
   * {@code s} selects.
   */
  private static int spanRequested(EditorModelCoupler emc) {
    ExecutionDataStack eds = emc.session().getExecutionDataStack();
    if(eds.size() > 0 && eds.peek() instanceof Integer) {
      return (int)eds.pop();
    }
    Integer count = emc.session().getPendingCount();
    return count == null ? 1 : count;
  }
}
