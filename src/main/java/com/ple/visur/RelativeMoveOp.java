package com.ple.visur;

import java.util.ArrayList;

public class RelativeMoveOp implements Operator {
  @Override
  public void execute(Object opInfo) {
    EditorModelCoupler emc = ServiceHolder.editorModelCoupler;
    ExecutionDataStack eds = emc.getExecutionDataStack();
    String editorContent = emc.getEditorContent();
    ArrayList<Integer> newlineIndices = emc.getNextLineIndices();
    int dy = (int)eds.pop();
    int dx = (int)eds.pop();
    Quantum cursorQuantum = emc.getCursorQuantum();
    MovementVector movementVector = new MovementVector(dx, dy);
    int newCA = cursorQuantum.move(editorContent, newlineIndices, movementVector);
    emc.putCA(newCA);
    int[] newBounds;
    if(newCA == 0 && editorContent.length() == 0) {
      newBounds = new int[]{newCA, newCA};
    } else {
      newBounds = cursorQuantum.getBoundaries(newCA, newlineIndices, emc.getSpan(), false);
    }
    emc.putCursorQuantumStartAndScroll(newBounds[0]);
    emc.putCursorQuantumEndAndScroll(newBounds[1]);
  }
}
