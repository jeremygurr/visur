package com.ple.visur;

public class YankSpanOp implements Operator {
  @Override
  public void execute(Object ignored) {
    EditorModelCoupler emc = ServiceHolder.editorModelCoupler;
    if (emc.getSpan() == 0) return;
    String content = emc.getEditorContent();
    String text = content.substring(emc.getCursorQuantumStart(), emc.getCursorQuantumEnd());
    emc.putGlobalVar("yankRegister", ObjectVisurVar.make(text));
  }
}
