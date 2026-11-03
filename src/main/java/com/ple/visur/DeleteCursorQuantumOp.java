package com.ple.visur;

import java.util.ArrayList;

public class DeleteCursorQuantumOp implements Operator {

  EditorModelCoupler emc = ServiceHolder.editorModelCoupler;

  @Override
  public void execute(Object opInfo) {
    int span = emc.getSpan();
    if(span > 0) {
      int startBound = emc.getCursorQuantumStart();
      int endBound = emc.getCursorQuantumEnd();
      String editorContent = emc.getEditorContent();
      String editorContentBeforeDeletedPortion = editorContent.substring(0, startBound);
      String editorContentAfterDeletedPortion = editorContent.substring(endBound);
      String resultingEditorContent = editorContentBeforeDeletedPortion + editorContentAfterDeletedPortion;
      emc.putEditorContent(resultingEditorContent);
      int ca = emc.getCA();
      if(ca >= resultingEditorContent.length()) {
        ca = resultingEditorContent.length() > 0 ? resultingEditorContent.length() - 1 : 0;
        emc.putCA(ca);
        emc.putVirtualCX(emc.getCX());
      }
      ArrayList<Integer> nl = emc.getNextLineIndices();
      Quantum cursorQuantum = emc.getCursorQuantum();
      int[] bounds = cursorQuantum.getBoundaries(ca, nl, span, false);
      emc.putCursorQuantumStartAndScroll(bounds[0]);
      emc.putCursorQuantumEndAndScroll(bounds[1]);
    }
  }

}
