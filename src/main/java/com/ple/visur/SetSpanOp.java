package com.ple.visur;

public class SetSpanOp implements Operator {
  @Override
  public void execute(Object opInfo) {
    EditorModelCoupler emc = ServiceHolder.editorModelCoupler;
    ExecutionDataStack eds = emc.getExecutionDataStack();
    int spanToSet = (int)eds.pop();
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
}
