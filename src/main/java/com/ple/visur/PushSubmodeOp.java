package com.ple.visur;

import java.util.Stack;

public class PushSubmodeOp implements Operator {
  @Override
  public void execute(Object opInfo) {
    EditorModelCoupler emc = ServiceHolder.editorModelCoupler;
    ExecutionDataStack eds = emc.session().getExecutionDataStack();
    String editorSubmodeAsString = (String)eds.pop();
    EditorSubmode editorSubmode = EditorSubmode.getSubmodeByString(editorSubmodeAsString);
    if(editorSubmode != null) {
      if(editorSubmode ==  EditorSubmode.navigate) {
        Stack<EditorSubmode> editorSubmodeStack = emc.session().getEditorSubmodeStack();
        while(editorSubmodeStack.size() > 1) {
          editorSubmodeStack.pop();
        }
        emc.session().putEditorSubmodeStack(editorSubmodeStack);
      } else {
        emc.session().putEditorSubmode(editorSubmode);
      }
    }
  }
}
