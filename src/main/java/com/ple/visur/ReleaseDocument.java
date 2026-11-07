package com.ple.visur;

import java.io.IOException;

/**
 * A server nobody is looking at holds no document.
 *
 * <p>The server outlives its clients on purpose — {@code ZQ} means "leave the server running" — and while it waited it
 * held somebody's file in memory indefinitely: unsaved work, and a document that a second {@code visur other.txt} then
 * had to share. So the last window to leave takes the text with it. Work that has a file goes to that file, which is
 * what the user who detaches rather than quits has already agreed to by leaving the file where the editor said it was.
 * Work with no file to go to is dropped, and the swap it wrote while typing is what makes that a disposal rather than a
 * loss: the next window on that name is asked about it.
 *
 * <p>Only a goodbye triggers this. A socket that died — killed terminal, dropped network, a browser reloading its tab —
 * is not a client that has decided anything about its work, and a rule that saved on those would write files at people
 * who never asked. See {@link Connections#markLeftOnPurpose}.
 */
public final class ReleaseDocument {

  private ReleaseDocument() {
  }

  /**
   * The last window said goodbye: write what has somewhere to go, then let the document go.
   *
   * <p>A save that fails stops the whole thing. The rule is about not holding a document, and holding one is the
   * cheaper failure next to throwing away text that never reached a file — and there is no window left to tell, which
   * is why the reason goes to the log.
   */
  static void afterTheLastWindowLeft(EditorModelCoupler emc) {
    Document document = emc.document();
    String path = document.getSourceFilePath();
    if (!document.isDirty()) {
      // Nothing to lose, so nothing to think about: the file it came from already has every character.
      dispose(emc);
      return;
    }
    if (path != null && !path.isEmpty() && !document.isReadOnly()) {
      try {
        SaveFileOp.write(document, path);
      } catch (IOException failure) {
        System.out.println("Document not released, save failed: " + failure.getMessage());
        return;
      }
      dispose(emc);
      return;
    }
    if (document.isReadOnly()) {
      // The window said it was only looking, and the text disagrees. Its swap holds an older version than this one —
      // that is how the buffer got this text — so there is nowhere this work can be put down. Keeping it is the only
      // answer that does not decide for somebody who has gone away.
      return;
    }
    // Work with no file to write it to is disposed of, and the swap this document wrote while it was being typed is
    // what makes that a disposal rather than a loss: the next window on that name is asked about it.
    dispose(emc);
  }

  /**
   * Empty, nameless, clean, no history: what a server holds when it is holding nothing.
   *
   * <p>Not {@link LoadFileOp#install}, which resets a window's cursor as well and has a window to do it for. The next
   * window starts at the beginning of this buffer by itself.
   */
  static void dispose(EditorModelCoupler emc) {
    Document document = emc.document();
    document.putReadOnly(false);
    // As saved, not as typed: the swap must not be rewritten with the empty text that is replacing this one, since a
    // swap is the only copy of the work being disposed of.
    document.putContentAsSaved("");
    document.setSourceFilePath("");
    document.getUndoHistory().clear();
  }
}
