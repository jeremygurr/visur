package com.ple.visur;

/**
 * {@code R}: walk forward one command. Any new edit clears what this can walk forward to, and the bottom of the undo
 * stack and the top of the redo stack are both no-ops.
 */
public class RedoOp implements Operator {
  @Override
  public void execute(Object ignored) {
    EditorModelCoupler emc = ServiceHolder.editorModelCoupler;
    emc.document().getUndoHistory().redo(emc);
  }
}
