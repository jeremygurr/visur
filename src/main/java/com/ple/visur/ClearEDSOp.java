package com.ple.visur;

public class ClearEDSOp implements Operator {
  @Override
  public void execute(Object opInfo) {
    EditorModelCoupler emc = ServiceHolder.editorModelCoupler;
    ExecutionDataStack eds = emc.session().getExecutionDataStack();
    while(eds.size() > 0) {
      eds.pop();
    }
  }
}
