package com.ple.visur;

/**
 * A command-line word that is not a command.
 *
 * <p>This op exists because its absence was the bug: an unrecognised word used to compile to nothing at all, so
 * {@code :frobnicate} looked precisely like a command that had worked, and the compile loop stopped at the first word
 * nothing matched without saying that it had. The message quotes the word back — "unknown command" without the word is
 * the user typing it again to find out which half they mistyped — and it goes to the message slot, since a failure the
 * screen does not show is a failure that did not happen.
 */
public class UnknownCommandOp implements Operator {
  @Override
  public void execute(Object opInfo) {
    ServiceHolder.editorModelCoupler.session().reportError(opInfo + " is not a command. :w saves, :open loads, :q leaves.");
  }
}
