package com.ple.visur;

/**
 * Escape's last act in an insert or replace session: write the copies the count owes. Does nothing at all when the
 * session was opened without a count above 1, which is every session the editor sees in ordinary use.
 *
 * <p>See {@link SessionText#repeatAtCaret} for the rule, and for why this runs after Escape has restored the span and
 * the cursor quantum.
 */
public class RepeatSessionTextOp implements Operator {
  @Override
  public void execute(Object ignored) {
    SessionText.repeatAtCaret(ServiceHolder.editorModelCoupler);
  }
}
