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

    // Loading replaces the buffer, so it is the one command that would destroy work nobody has saved. Visur has no
    // question to ask — a question needs an answer, and the message slot has never had one — so it refuses and names the
    // way round it. The check lives here rather than in the grammar because this is the only door in.
    if (emc.document().isDirty()) {
      emc.session().reportError(":open refused: unsaved work. :w saves it first.");
      return;
    }

    if (eds.size() == 0) {
      emc.session().reportError(":open needs a path: :open /path/to/file");
      return;
    }

    String filePath = (String) eds.pop();
    try {
      Path path = Paths.get(filePath);
      if (!Files.exists(path)) {
        emc.session().reportError("loadFile: file not found: " + filePath);
        return;
      }
      if (!Files.isRegularFile(path)) {
        emc.session().reportError("loadFile: not a regular file: " + filePath);
        return;
      }
      Path swap = SwapFiles.beside(path.toFile());
      if (Files.exists(swap)) {
        // The file is not the whole story, and there is no safe guess about which half to believe. The open stops here
        // rather than loading and complaining afterwards, because afterwards there is only one version left to choose.
        RecoveryOffer offer = new RecoveryOffer(filePath, swap);
        emc.session().putPendingOffer(offer);
        emc.session().putMessage(offer.question());
        return;
      }

      String content = Files.readString(path, StandardCharsets.UTF_8);
      install(emc, filePath, content, true);
    } catch (IOException e) {
      emc.session().reportError("loadFile: " + e.getMessage());
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
  static void install(EditorModelCoupler emc, String filePath, String content, boolean asSaved) {
    emc.document().putReadOnly(false);
    if (asSaved) {
      emc.document().putContentAsSaved(content);
    } else {
      emc.document().putContent(content);
    }

    emc.session().putCA(0);
    emc.session().putVirtualCX(0);
    emc.session().putCanvasStart(0);
    emc.session().putFY(0);

    emc.document().updateNextLineIndices();
    emc.session().putSpan(content.length() > 0 ? 1 : 0);

    // This file becomes the new remembered source, which also puts down the swap of the name before it.
    emc.document().setSourceFilePath(filePath);

    // A different document is not this document's history: u would paste one file's text into another's.
    emc.document().getUndoHistory().clear();
  }
}
