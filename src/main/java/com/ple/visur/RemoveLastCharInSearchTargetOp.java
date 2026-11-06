package com.ple.visur;

public class RemoveLastCharInSearchTargetOp implements Operator {
  @Override
  public void execute(Object opInfo) {
    EditorModelCoupler emc = ServiceHolder.editorModelCoupler;
    ExecutionDataStack eds = emc.session().getExecutionDataStack();
    if(eds.size() > 0) {
      String oldSearchTarget = (String) eds.pop();
      String newSearchTarget = oldSearchTarget.substring(0, oldSearchTarget.length() - 1);
      eds.push(newSearchTarget);
    }
  }
}
