package com.ple.visur;

/**
 * Keeps the count typed before an insert-entering key alive for the session it opens, and opens the session's recording
 * when that count is big enough to repeat anything.
 *
 * <p>The count would otherwise die with that very keypress: Escape's {@code "navigate" changeMode} lands before
 * {@link CountPrefix#retireUnlessDigit}, so by the time a session ends the pending count is gone. A session that
 * repeats what was typed — {@code 3 g} — has to copy the count out on the way in.
 *
 * <p>Copying nothing is the other half of the job. A count from the last session would repeat text this session never
 * typed, so a count of one or none clears the session state entirely rather than leaving it behind. Every key that
 * opens an insert or replace session stashes this way, which is what keeps the variables from outliving the session
 * they belong to.
 */
public class StashCountOp implements Operator {
  /** What goes between the copies this session repeats: nothing for {@code i}, {@code a} and {@code o}, the newline
   * {@code g} and {@code G} opened the line with. */
  private final String separator;

  public StashCountOp(String separator) {
    this.separator = separator;
  }

  @Override
  public void execute(Object ignored) {
    EditorModelCoupler emc = ServiceHolder.editorModelCoupler;
    Integer count = emc.session().getPendingCount();
    if (count == null || count <= 1) {
      SessionText.discard(emc);
      return;
    }
    emc.putGlobalVar("tempCount", ObjectVisurVar.make(count));
    // The count belongs to this key, and the session outlives it: retireUnlessDigit leaves a pending count alone
    // while a mode other than navigate is open, so `3 i` would otherwise go on spending its three on every character
    // typed — 3 to the right, and 3 deleted by a backspace.
    emc.session().clearPendingCount();
    SessionText.begin(emc, separator);
  }
}
