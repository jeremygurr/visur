package com.ple.visur;

public class SearchForwardOp implements Operator {
  @Override
  public void execute(Object opInfo) {
    EditorModelCoupler emc = ServiceHolder.editorModelCoupler;
    ExecutionDataStack eds = emc.session().getExecutionDataStack();
    String searchTarget = "";
    if (eds.size() > 0) {
      searchTarget = (String) eds.pop();
    }
    if (searchTarget.equals("")) {
      System.out.println("No search target found");
    } else {
      System.out.println("forward search target = " + searchTarget);
      String editorContent = emc.getEditorContent();
      Quantum scopeQuantum = emc.getScopeQuantum();
      int[] scopeQuantumBounds = scopeQuantum.getBoundaries(emc.getCA(), emc.getNextLineIndices(), 1, false); //span is always 1 when searching within a scopeQuantum
      int scopeQuantumEnd = scopeQuantumBounds[1];
      int cursorQuantumEnd = emc.session().getCursorQuantumEnd();
      if(cursorQuantumEnd < scopeQuantumEnd) {
        String editorContentSubstringToSearch = editorContent.substring(cursorQuantumEnd + 1, scopeQuantumEnd);
        int foundResult = editorContentSubstringToSearch.indexOf(searchTarget);
        if (foundResult > -1) {
          int foundIndex = foundResult + cursorQuantumEnd + 1;
          emc.putCA(foundIndex);
          emc.session().putVirtualCX(emc.getCX());
          Quantum cursorQuantum = emc.getCursorQuantum();
          int[] newBounds = cursorQuantum.getBoundaries(emc.getCA(), emc.getNextLineIndices(), emc.getSpan(), false);
          emc.session().putCursorQuantumStartAndScroll(newBounds[0]);
          emc.session().putCursorQuantumEndAndScroll(newBounds[1]);
        }
      }
    }
    emc.putPreviousSearchTarget(searchTarget);
    emc.session().putPreviousSearchDirectionWasForward(true);
  }
}
