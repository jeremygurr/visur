package com.ple.visur;

public class ChangeCursorQuantumOp implements Operator {

  @Override
  public void execute(Object opInfo) {
    EditorModelCoupler emc = ServiceHolder.editorModelCoupler;
    String cursorQuantumName = getQuantumName(emc);
    if(cursorQuantumName != null) {
      Quantum targetQuantum = emc.getQuantumNameToQuantum().get(cursorQuantumName);
      int[] bounds = targetQuantum.getBoundaries(emc.getCA(), emc.getNextLineIndices(), emc.getSpan(), false);
      emc.putCursorQuantum(targetQuantum);
      emc.session().putCursorQuantumStartAndScroll(bounds[0]);
      emc.session().putCursorQuantumEndAndScroll(bounds[1]);
    }
  }

  public String getQuantumName(EditorModelCoupler emc) {
    String topElement = (String)emc.getExecutionDataStack().peek();
    String qName;
    boolean topElementIsInvalidQuantumName = emc.getQuantumNameToQuantum().get(topElement) == null;
    if(topElementIsInvalidQuantumName) {
      boolean quantumGlobalVarExistsWithTopElementName = emc.session().getGlobalVar(topElement).getVal() instanceof Quantum;
      if(quantumGlobalVarExistsWithTopElementName) {
        ObjectVisurVar qOVV = (ObjectVisurVar) emc.session().getGlobalVar(topElement);
        Quantum q = (Quantum)qOVV.getVal();
        qName = q.getName();
      } else {
        return null;
      }
    } else {
      qName = (String) emc.getExecutionDataStack().pop();;
    }
    return qName;
  }

}
