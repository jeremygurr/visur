package com.ple.visur;

/**
 * {@code u}: walk back one command. The command is the unit, so an insert session comes out whole — the line {@code g}
 * opened, the typing, and a count's repeat together.
 */
public class UndoOp implements Operator {
  @Override
  public void execute(Object ignored) {
    EditorModelCoupler emc = ServiceHolder.editorModelCoupler;
    emc.getUndoHistory().undo(emc);
  }
}
