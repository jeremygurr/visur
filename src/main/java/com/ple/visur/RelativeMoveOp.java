package com.ple.visur;

import java.util.ArrayList;

public class RelativeMoveOp implements Operator {
  @Override
  public void execute(Object opInfo) {
    EditorModelCoupler emc = ServiceHolder.editorModelCoupler;
    ExecutionDataStack eds = emc.session().getExecutionDataStack();
    String editorContent = emc.document().getContent();
    ArrayList<Integer> newlineIndices = emc.document().getNextLineIndices();
    int dy = (int)eds.pop();
    int dx = (int)eds.pop();
    Quantum cursorQuantum = emc.session().getCursorQuantum();
    // The count decides how far; a quantum's move spends the vector it is handed, so it is called once per unit
    // rather than taught about counts. A count of zero is a movement that does not happen.
    int units = CountPrefix.countOrOne(emc);
    int newCA = emc.session().getCA();
    for (int unit = 0; unit < units; unit++) {
      newCA = cursorQuantum.move(editorContent, newlineIndices, new MovementVector(dx, dy));
    }
    emc.session().putCA(newCA);
    int[] newBounds;
    if(newCA == 0 && editorContent.length() == 0) {
      newBounds = new int[]{newCA, newCA};
    } else {
      newBounds = cursorQuantum.getBoundaries(newCA, newlineIndices, emc.session().getSpan(), false);
    }
    emc.session().putCursorQuantumStartAndScroll(newBounds[0]);
    emc.session().putCursorQuantumEndAndScroll(newBounds[1]);
  }
}
