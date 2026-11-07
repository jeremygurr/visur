package com.ple.visur;

/**
 * {@code :q} — leave, if leaving would cost nobody anything.
 *
 * <p>What this quits is <strong>the window, not the server</strong>. The connection closes normally and the terminal
 * client reads that as a detach (exit code 0), which is what its launcher reads as "leave the server running":
 * {@code :q} and {@code ZQ} then mean the same thing, one typed in the editor and one in the terminal. Stopping the
 * process stays {@code ZD}'s, because the launcher started it and a browser tab has no business ending a process it
 * cannot see — and because a command that stops a server other windows are using is a command somebody loses a session
 * to within a week of it shipping.
 *
 * <p>Unsaved work is refused rather than asked about: visur has no surface for a question, and a message naming
 * {@code :q!} is a question with its answer printed underneath.
 */
public class QuitOp implements Operator {
  @Override
  public void execute(Object opInfo) {
    EditorModelCoupler emc = ServiceHolder.editorModelCoupler;
    if(emc.document().isDirty()) {
      emc.session().reportError(":q refused: unsaved work. :w saves it, :q! throws it away.");
      return;
    }
    emc.session().putQuitWasRequested(true);
  }
}
