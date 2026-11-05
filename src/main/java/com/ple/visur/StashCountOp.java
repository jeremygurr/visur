package com.ple.visur;

/**
 * Keeps the count typed before an insert-entering key alive for the session it opens.
 *
 * <p>The count would otherwise die with that very keypress: Escape's {@code "navigate" changeMode} lands before
 * {@link CountPrefix#retireUnlessDigit}, so by the time a session ends the pending count is gone. A session that wants
 * to repeat what was typed — {@code 3 g}, [05](../../../../docs/wayfinder/separators-and-undo/issues/05-insert-repeat.md)
 * — has to copy the count out on the way in.
 *
 * <p>Copying nothing is the other half of the job. A count from the last session would repeat text this session never
 * typed, so typing no count clears what is there. Every key that opens an insert or replace session stashes this way,
 * which is what keeps the variable from outliving the session it belongs to.
 */
public class StashCountOp implements Operator {
  @Override
  public void execute(Object ignored) {
    EditorModelCoupler emc = ServiceHolder.editorModelCoupler;
    Integer count = emc.getPendingCount();
    if (count == null) {
      emc.getGlobalVarMap().remove("tempCount");
    } else {
      emc.putGlobalVar("tempCount", ObjectVisurVar.make(count));
    }
  }
}
