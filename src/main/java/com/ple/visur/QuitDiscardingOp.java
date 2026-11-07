package com.ple.visur;

/**
 * {@code :q!} — leave, and throw away what was never saved.
 *
 * <p>The swap goes with the work, and only when the work really is being thrown away. That last part is what a shared
 * document forces: a swap belongs to the document, and {@code :q!} closes a window, so while another window is still
 * editing this text its insurance stays — deleting it would leave the window that stayed behind one crash away from
 * losing the file, which is not what anybody who typed {@code :q!} into a second tab agreed to.
 *
 * <p>The file being edited is never touched. Discarding a buffer says nothing about what is on disk, and only a save
 * ever speaks for that.
 */
public class QuitDiscardingOp implements Operator {
  @Override
  public void execute(Object opInfo) {
    EditorModelCoupler emc = ServiceHolder.editorModelCoupler;
    if (Connections.count() <= 1) {
      emc.document().deleteSwap();
      // And the buffer goes with it, before the server notices the window is gone: the release that follows saves what
      // is unsaved, and "throw it away" cannot be answered by a write. While another window is still editing this text
      // it is not this window's to throw away, so nothing here happens.
      ReleaseDocument.dispose(emc);
    }
    emc.session().putQuitWasRequested(true);
  }
}
