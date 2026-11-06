package com.ple.visur;

public class RemoveGlobalVarOp implements Operator {
  @Override
  public void execute(Object opInfo) {
    EditorModelCoupler emc = ServiceHolder.editorModelCoupler;
    ExecutionDataStack eds = emc.session().getExecutionDataStack();
    String nameOfElementToRemove = (String)eds.pop();
    VariableMap gvm = emc.session().getGlobalVarMap();
    gvm.remove(nameOfElementToRemove);
  }
}
