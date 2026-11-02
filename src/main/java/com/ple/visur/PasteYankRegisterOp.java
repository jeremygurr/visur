package com.ple.visur;

public class PasteYankRegisterOp implements Operator {
  @Override
  public void execute(Object ignored) {
    EditorModelCoupler emc = ServiceHolder.editorModelCoupler;
    VisurVar register = emc.getGlobalVar("yankRegister");
    if (register == null) return;
    int insertionPoint = emc.getCursorQuantumEnd();
    TextInsertion.insertAt(emc, (String) register.getVal(), insertionPoint);
  }
}
