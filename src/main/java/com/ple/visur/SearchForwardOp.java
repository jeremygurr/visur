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
      String editorContent = emc.document().getContent();
      Quantum scopeQuantum = emc.session().getScopeQuantum();
      int[] scopeQuantumBounds = scopeQuantum.getBoundaries(emc.session().getCA(), emc.document().getNextLineIndices(), 1, false); //span is always 1 when searching within a scopeQuantum
      int scopeQuantumEnd = scopeQuantumBounds[1];
      int cursorQuantumEnd = emc.session().getCursorQuantumEnd();
      if(cursorQuantumEnd < scopeQuantumEnd) {
        String editorContentSubstringToSearch = editorContent.substring(cursorQuantumEnd + 1, scopeQuantumEnd);
        int foundResult = editorContentSubstringToSearch.indexOf(searchTarget);
        if (foundResult > -1) {
          int foundIndex = foundResult + cursorQuantumEnd + 1;
          emc.session().putCA(foundIndex);
          emc.session().putVirtualCX(emc.session().getCX());
          Quantum cursorQuantum = emc.session().getCursorQuantum();
          int[] newBounds = cursorQuantum.getBoundaries(emc.session().getCA(), emc.document().getNextLineIndices(), emc.session().getSpan(), false);
          emc.session().putCursorQuantumStartAndScroll(newBounds[0]);
          emc.session().putCursorQuantumEndAndScroll(newBounds[1]);
        }
      }
    }
    emc.session().putPreviousSearchTarget(searchTarget);
    emc.session().putPreviousSearchDirectionWasForward(true);
  }
}
