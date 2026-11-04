package com.ple.visur;

import java.util.ArrayList;

public class DeleteCursorQuantumOp implements Operator {

  EditorModelCoupler emc = ServiceHolder.editorModelCoupler;

  @Override
  public void execute(Object opInfo) {
    // One guard for both refusals: no selection, or a count of zero, names no text, and an editor that deletes text
    // it never highlighted is an editor nobody trusts with a count.
    int[] extent = emc.getCursorQuantum().operatorExtent();
    int startBound = extent[0];
    int endBound = extent[1];
    if(endBound > startBound) {
      String editorContent = emc.getEditorContent();
      String deleted = editorContent.substring(startBound, endBound);
      String resultingEditorContent = editorContent.substring(0, startBound) + editorContent.substring(endBound);
      emc.putEditorContent(resultingEditorContent);
      // What was removed goes where a yank puts it, so p can put it back. One unnamed register, so d clobbers y by
      // design; named registers are the map's fog.
      emc.putGlobalVar("yankRegister", ObjectVisurVar.make(deleted));
      int ca = emc.getCA();
      if(ca >= resultingEditorContent.length()) {
        ca = resultingEditorContent.length() > 0 ? resultingEditorContent.length() - 1 : 0;
        emc.putCA(ca);
        emc.putVirtualCX(emc.getCX());
      }
      ArrayList<Integer> nl = emc.getNextLineIndices();
      Quantum cursorQuantum = emc.getCursorQuantum();
      int[] bounds = cursorQuantum.getBoundaries(ca, nl, emc.getSpan(), false);
      emc.putCursorQuantumStartAndScroll(bounds[0]);
      emc.putCursorQuantumEndAndScroll(bounds[1]);
    }
  }

}
