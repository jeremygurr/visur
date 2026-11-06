package com.ple.visur;

public class ReplaceCharOp implements Operator {
  @Override
  public void execute(Object opInfo) {
    EditorModelCoupler emc = ServiceHolder.editorModelCoupler;
    ExecutionDataStack eds = emc.session().getExecutionDataStack();
    String editorContent = emc.document().getContent();
    int ca = emc.session().getCA();
    if(editorContent.charAt(ca) != '\n') {
      String contentBeforeChar = editorContent.substring(0, ca);
      String replacingChar = (String) eds.pop();
      String contentAfterChar = editorContent.substring(ca + 1, editorContent.length());
      String resultingEditorContent = contentBeforeChar + replacingChar + contentAfterChar;
      emc.document().putContent(resultingEditorContent);
    }
  }
}
