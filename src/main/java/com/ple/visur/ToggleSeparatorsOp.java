package com.ple.visur;

/** Toggle the session's separator-inclusion preference. */
public class ToggleSeparatorsOp implements Operator {
  @Override
  public void execute(Object opInfo) {
    EditorModelCoupler emc = ServiceHolder.editorModelCoupler;
    boolean on = emc.getGlobalVar("includeSeparators") == null
      || Boolean.TRUE.equals(emc.getGlobalVar("includeSeparators").getVal());
    emc.putGlobalVar("includeSeparators", ObjectVisurVar.make(!on));
  }
}
