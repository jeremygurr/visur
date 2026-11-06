package com.ple.visur;

/** Toggle the session's separator-inclusion preference. */
public class ToggleSeparatorsOp implements Operator {
  @Override
  public void execute(Object opInfo) {
    EditorModelCoupler emc = ServiceHolder.editorModelCoupler;
    boolean on = emc.session().getGlobalVar("includeSeparators") == null
      || Boolean.TRUE.equals(emc.session().getGlobalVar("includeSeparators").getVal());
    emc.session().putGlobalVar("includeSeparators", ObjectVisurVar.make(!on));
  }
}
