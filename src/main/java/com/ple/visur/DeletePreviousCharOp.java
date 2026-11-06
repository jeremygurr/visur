package com.ple.visur;

public class DeletePreviousCharOp implements Operator {

  EditorModelCoupler emc = ServiceHolder.editorModelCoupler;

  @Override
  public void execute(Object opInfo) {
    int ca = emc.session().getCA();
    String editorContent = emc.document().getContent();
    String editorContentBeforeChar = ca > 0 ? editorContent.substring(0, ca - 1) : "";
    String editorContentAfterChar = editorContent.substring(ca);
    String resultingEditorContent = editorContentBeforeChar + editorContentAfterChar;
    emc.document().putContent(resultingEditorContent);
    emc.session().putCA(ca);
    SessionText.unrecord(emc);
  }

}
