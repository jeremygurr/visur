package com.ple.visur;

public class ChangeScopeOp implements Operator {
  @Override
  public void execute(Object opInfo) {
    EditorModelCoupler emc = ServiceHolder.editorModelCoupler;
    ExecutionDataStack eds = emc.session().getExecutionDataStack();
    String scopeName = (String) eds.pop();
    System.out.println("scope = " + scopeName);
    QuantumNameToQuantum quantumNameToQuantum = emc.getQuantumNameToQuantum();
    Quantum scopeQuantum = quantumNameToQuantum.get(scopeName);
    if(scopeQuantum != null) {
      emc.session().putScopeQuantum(scopeQuantum);
    }
  }
}
