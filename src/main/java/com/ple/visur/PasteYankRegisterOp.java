package com.ple.visur;

public class PasteYankRegisterOp implements Operator {
  @Override
  public void execute(Object ignored) {
    EditorModelCoupler emc = ServiceHolder.editorModelCoupler;
    VisurVar register = emc.getGlobalVar("yankRegister");
    if (register == null) return;
    String value = (String) register.getVal();
    // 3 p is three copies. One insertion of three copies lands the same text as three insertions at successive
    // positions, without the cursor having to be re-seated twice.
    TextInsertion.insertAt(emc, value.repeat(CountPrefix.countOrOne(emc)), emc.getCursorQuantumEnd());
  }
}
