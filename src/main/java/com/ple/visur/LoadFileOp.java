package com.ple.visur;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Loads a file and replaces the document body with it.
 *
 * <p>Requires a quoted string argument: {@code ;"path" loadFile}.
 * A read failure (missing, undecodable) reports to the server console and leaves
 * the current document untouched — a failed load never destroys in-memory work.
 *
 * <p>On success: cursor to (0,0), line indices recomputed, canvas/focus reset,
 * the file becomes the remembered source file.
 */
public class LoadFileOp implements Operator {
  @Override
  public void execute(Object opInfo) {
    EditorModelCoupler emc = ServiceHolder.editorModelCoupler;
    ExecutionDataStack eds = emc.session().getExecutionDataStack();

    // loadFile requires a path argument
    if (eds.size() == 0) {
      emc.session().reportError("loadFile requires a path argument: ;\"path\" loadFile");
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
      byte[] bytes = Files.readAllBytes(path);
      String content = new String(bytes, StandardCharsets.UTF_8);
      // Replace the document content. As-saved: this text is what the file holds, so it is not unsaved work.
      emc.document().putContentAsSaved(content);

      // Reset cursor to (0,0)
      emc.session().putCA(0);
      emc.session().putVirtualCX(0);
      emc.session().putCanvasStart(0);
      emc.session().putFY(0);

      // Recompute line indices (this handles newline tracking)
      emc.document().updateNextLineIndices();

      // Update span based on content length
      if (content.length() > 0) {
        emc.session().putSpan(1);
      } else {
        emc.session().putSpan(0);
      }

      // This file becomes the new remembered source
      emc.document().setSourceFilePath(filePath);

      // A different document is not this document's history: u would paste one file's text into another's.
      emc.document().getUndoHistory().clear();

      // Exit command state (it's been executed successfully)
      // Note: CommandStateService.exitCommandState() handles this

    } catch (IOException e) {
      emc.session().reportError("loadFile: " + e.getMessage());
    }
  }
}
