package com.ple.visur;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Loads a file and replaces the document body with it.
 *
 * <p>Takes its path from the command stack, which {@code :open path} fills.
 *
 * <p>Refuses while the document has unsaved work, and a read failure (missing, undecodable) leaves the current document
 * untouched — a load never destroys in-memory work, whether it was refused or failed.
 *
 * <p>On success: cursor to (0,0), line indices recomputed, canvas/focus reset,
 * the file becomes the remembered source file.
 */
public class LoadFileOp implements Operator {
  @Override
  public void execute(Object opInfo) {
    EditorModelCoupler emc = ServiceHolder.editorModelCoupler;
    ExecutionDataStack eds = emc.session().getExecutionDataStack();

    // Loading replaces the buffer, so it is the one command that can destroy work nobody has saved. It refuses rather
    // than asks: a swap is a question about which of two versions to believe, and this is not that question — the text
    // in hand is the only version, and `:w` is three keys. The check lives here rather than in the grammar because this
    // is the only door in, and it stays in the door rather than moving into `open`, because a launch load onto an empty
    // document has no unsaved work to refuse and must not be told it does.
    if (emc.document().isDirty()) {
      emc.session().reportError(":open refused: unsaved work. :w saves it first.");
      return;
    }

    if (eds.size() == 0) {
      emc.session().reportError(":open needs a path: :open /path/to/file");
      return;
    }

    open(emc, emc.session(), (String) eds.pop());
  }

  /**
   * Open a path in front of a window: the checks, then {@link #install}, and nothing else.
   *
   * <p>A path that is not on disk is a document with no text, whichever door it came through — the rule vim has, and
   * the reason {@code visur notes.txt} and {@code :open notes.txt} cannot disagree. What protects a typo is elsewhere:
   * {@code :open} is refused while the document has unsaved work, so walking away from a buffer takes a decision
   * somebody has already made about it.
   *
   * <p>The window is an argument rather than {@code emc.session()} because a launch has no adopted connection yet:
   * the file boot was asked for is opened by the first window to arrive, and that window is not the one the keyboard
   * belongs to.
   */
  static void open(EditorModelCoupler emc, Session window, String filePath) {
    try {
      Path path = Paths.get(filePath);
      if (!Files.exists(path)) {
        // No file, so no text, and the name is kept so the first :w has somewhere to go. Nothing is created on disk:
        // an empty buffer nobody saves leaves no trace, which is the other half of why a typo here is cheap.
        install(emc, window, filePath, "", true);
        return;
      }
      if (!Files.isRegularFile(path)) {
        window.reportError("loadFile: not a regular file: " + filePath);
        return;
      }
      Path swap = SwapFiles.beside(path.toFile());
      if (Files.exists(swap)) {
        // The file is not the whole story, and there is no safe guess about which half to believe. The open stops here
        // rather than loading and complaining afterwards, because afterwards there is only one version left to choose.
        RecoveryOffer offer = new RecoveryOffer(filePath, swap);
        window.putPendingOffer(offer);
        window.putMessage(offer.question());
        return;
      }

      String content = Files.readString(path, StandardCharsets.UTF_8);
      install(emc, window, filePath, content, true);
    } catch (IOException e) {
      window.reportError("loadFile: " + e.getMessage());
    }
  }

  /**
   * Make this text be this file's document: content, cursor, scroll window, undo history, source path.
   *
   * <p>{@code asSaved} is the whole difference between opening a file and recovering a swap: one is what the disk
   * holds, the other is text the disk has never seen, and only the first earns a clean mode line.
   *
   * <p>Static and public to the package because an answer to a {@link RecoveryOffer} opens a file too, and a second
   * copy of this reset is a second chance for the two doors to disagree about where the cursor goes.
   */
  static void install(EditorModelCoupler emc, Session window, String filePath, String content, boolean asSaved) {
    emc.document().putReadOnly(false);
    if (asSaved) {
      emc.document().putContentAsSaved(content);
    } else {
      emc.document().putContent(content);
    }

    window.putCA(0);
    window.putVirtualCX(0);
    window.putCanvasStart(0);
    window.putFY(0);

    emc.document().updateNextLineIndices();
    window.putSpan(content.length() > 0 ? 1 : 0);

    // This file becomes the new remembered source, which also puts down the swap of the name before it.
    emc.document().setSourceFilePath(filePath);

    // A different document is not this document's history: u would paste one file's text into another's.
    emc.document().getUndoHistory().clear();
  }
}
