package com.ple.visur;

public class ChangeModeOp implements Operator {
  @Override
  public void execute(Object opInfo) {
    EditorModelCoupler emc = ServiceHolder.editorModelCoupler;
    ExecutionDataStack eds = emc.session().getExecutionDataStack();
    String editorModeAsString = (String)eds.peek();
    EditorMode editorMode = EditorMode.getModeByString(editorModeAsString);
    if(editorMode != null) {
      emc.session().putEditorMode(editorMode);
    }
    PushSubmodeOp pushSubmodeOp = new PushSubmodeOp();
    pushSubmodeOp.execute(opInfo);
  }
}
