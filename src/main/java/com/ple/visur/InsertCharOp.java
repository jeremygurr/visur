package com.ple.visur;

public class InsertCharOp implements Operator {

  @Override
  public void execute(Object opInfo) {
    EditorModelCoupler emc = ServiceHolder.editorModelCoupler;
    ExecutionDataStack eds = emc.getExecutionDataStack();
    String charToBeInserted = (String) eds.pop();
    String editorContent = emc.getEditorContent();
    int ca = emc.getCA();
    String contentBeforeChar = editorContent.substring(0, ca);
    String contentAfterChar = editorContent.substring(ca, editorContent.length());
    String resultingEditorContent = contentBeforeChar + charToBeInserted + contentAfterChar;
    emc.putEditorContent(resultingEditorContent);
    SessionText.record(emc, charToBeInserted);
  }

}
