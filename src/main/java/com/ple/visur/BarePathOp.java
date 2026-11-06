package com.ple.visur;

/**
 * The path the user typed after a command, put on the stack for the command to take.
 *
 * <p>Deliberately not {@link LiteralStringOp}, which strips its first and last character because it is compiled from
 * source text that arrived carrying quotes. A path typed as {@code :w notes.txt} carries none, and going through that
 * op would hand {@code SaveFileOp} the filename {@code otes.tx}. Quotes are the command line's business — it takes them
 * off in {@link CommandCompileService#compileCommandLine} — so by the time this runs what is left is the path.
 */
public class BarePathOp implements Operator {
  @Override
  public void execute(Object opInfo) {
    ServiceHolder.editorModelCoupler.session().putOnExecutionDataStack((String) opInfo);
  }
}
