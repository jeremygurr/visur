package com.ple.visur;

public class SearchBackwardOp implements Operator {
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
      System.out.println("backward search target = " + searchTarget);
      String editorContent = emc.document().getContent();
      Quantum scopeQuantum = emc.session().getScopeQuantum();
      int[] scopeQuantumBounds = scopeQuantum.getBoundaries(emc.session().getCA(), emc.document().getNextLineIndices(), 1, false); //span is always 1 when searching within a scopeQuantum
      int scopeQuantumStart = scopeQuantumBounds[0];
      int cursorQuantumStart = emc.session().getCursorQuantumStart();
      if(cursorQuantumStart > scopeQuantumStart) {
        String editorContentSubstringToSearch = editorContent.substring(scopeQuantumStart, cursorQuantumStart);
        int foundResult = editorContentSubstringToSearch.lastIndexOf(searchTarget);
        if (foundResult > -1) {
          int foundIndex = scopeQuantumStart + foundResult; //this is not necessary if the two are truly equal. This is temp for debugging
          emc.session().putCA(foundIndex);
          emc.session().putVirtualCX(emc.session().getCX());
          Quantum cursorQuantum = emc.session().getCursorQuantum();
          int[] newBounds = cursorQuantum.getBoundaries(emc.session().getCA(), emc.document().getNextLineIndices(), emc.session().getSpan(), false);
          emc.session().putCursorQuantumStartAndScroll(newBounds[0]);
          emc.session().putCursorQuantumEndAndScroll(newBounds[1]);
        }
      }
    }
  }
}
