package com.ple.visur;

public class SetSpanOp implements Operator {
  @Override
  public void execute(Object opInfo) {
    EditorModelCoupler emc = ServiceHolder.editorModelCoupler;
    ExecutionDataStack eds = emc.getExecutionDataStack();
    int spanToSet = (int)eds.pop();
    spanToSet = spanToSet > 0 ? 1 : 0;
    System.out.println("span was switched to = " + spanToSet);
    int[] bounds = emc.getCursorQuantum().getBoundaries(emc.getCA(), emc.getNextLineIndices(), spanToSet, false);
    emc.putCursorQuantumStartAndScroll(bounds[0]);
    emc.putCursorQuantumEndAndScroll(bounds[1]);
    if(spanToSet == 0) {
      emc.putSpan(0);
    } else if(!SpanCollapse.zeroIfNothingSelected(emc, bounds)) {
      emc.putSpan(1);
    }
  }
}
