package com.ple.visur;

public class SetSpanOp implements Operator {
  @Override
  public void execute(Object opInfo) {
    EditorModelCoupler emc = ServiceHolder.editorModelCoupler;
    int spanToSet = spanRequested(emc);
    int[] bounds = emc.getCursorQuantum().getBoundaries(emc.getCA(), emc.getNextLineIndices(), spanToSet, false);
    emc.putCursorQuantumStartAndScroll(bounds[0]);
    emc.putCursorQuantumEndAndScroll(bounds[1]);
    if(spanToSet == 0 || SpanCollapse.zeroIfNothingSelected(emc, bounds)) {
      emc.putSpan(0);
    } else {
      // the requested span is what the model keeps, even when the range clamped short of it at the content end
      emc.putSpan(spanToSet);
    }
  }

  /**
   * Where the span came from: an operand, when a compiled binding names one itself ({@code 0 setSpan} is how {@code i},
   * {@code a} and {@code o} enter insert mode); then the count typed before the key; then one, which is what a bare
   * {@code s} selects.
   */
  private static int spanRequested(EditorModelCoupler emc) {
    ExecutionDataStack eds = emc.getExecutionDataStack();
    if(eds.size() > 0 && eds.peek() instanceof Integer) {
      return (int)eds.pop();
    }
    Integer count = emc.getPendingCount();
    return count == null ? 1 : count;
  }
}
