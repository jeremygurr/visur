package com.ple.visur;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.channels.FileChannel;

/**
 * Saves the current document to file.
 *
 * <p>Bare {@code ;saveFile} writes to the remembered (source) file.
 * {@code ;"path" saveFile} writes to that path and makes it the new remembered file.
 * No remembered file and no argument → error, nothing written.
 *
 * <p>Writes are crash-safe: temp file in the same directory, fsync, atomic rename.
 */
public class SaveFileOp implements Operator {
  @Override
  public void execute(Object opInfo) {
    EditorModelCoupler emc = ServiceHolder.editorModelCoupler;
    String content = emc.document().getContent();
    ExecutionDataStack eds = emc.session().getExecutionDataStack();

    // Pop optional path argument
    String targetPath = null;
    if (eds.size() > 0) {
      targetPath = (String) eds.pop();
    }

    String filePath = targetPath;
    if (filePath == null || filePath.isEmpty()) {
      filePath = emc.document().getSourceFilePath();
    }
    if (filePath == null || filePath.isEmpty()) {
      emc.session().reportError("saveFile: no path — neither an argument nor a remembered (source) file.");
      return;
    }

    Path tempFile = null;
    try {
      Path target = Paths.get(filePath);
      Path parent = target.getParent();
      if (parent != null) {
        Files.createDirectories(parent);
      }
      // Atomic write: temp file + rename.
      // Note: swap file (.visur-swap) is maintained by EditorModelCoupler.putEditorContent()
      // during editing. On successful save, we delete the swap so the source file is authoritative.
      tempFile = Files.createTempFile(parent, "visur-", ".tmp");
      // Write content to temp file
      Files.write(tempFile, content.getBytes(StandardCharsets.UTF_8),
        StandardOpenOption.WRITE, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
      // fsync: open the channel and force
      try (FileChannel fc = FileChannel.open(tempFile, StandardOpenOption.WRITE)) {
        fc.force(true);
      }
      // Atomic rename to target (replaces existing)
      Files.move(tempFile, target, StandardCopyOption.REPLACE_EXISTING);
      // Delete swap file after successful save (source file is now authoritative).
      Path swapPath = target.resolveSibling(target.getFileName().toString() + ".visur-swap");
      try { Files.delete(swapPath); } catch (IOException ignored) {}
      // Update remembered source path
      emc.document().setSourceFilePath(filePath);
    } catch (IOException e) {
      if (tempFile != null) {
        try { Files.deleteIfExists(tempFile); } catch (IOException ignored) {}
      }
      emc.session().reportError("saveFile: " + e.getMessage());
    }
  }
}
