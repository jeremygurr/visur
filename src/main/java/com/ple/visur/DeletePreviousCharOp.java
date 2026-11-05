package com.ple.visur;

public class DeletePreviousCharOp implements Operator {

  EditorModelCoupler emc = ServiceHolder.editorModelCoupler;

  @Override
  public void execute(Object opInfo) {
    int ca = emc.getCA();
    String editorContent = emc.getEditorContent();
    String editorContentBeforeChar = ca > 0 ? editorContent.substring(0, ca - 1) : "";
    String editorContentAfterChar = editorContent.substring(ca);
    String resultingEditorContent = editorContentBeforeChar + editorContentAfterChar;
    emc.putEditorContent(resultingEditorContent);
    emc.putCA(ca);
    SessionText.unrecord(emc);
  }

}
