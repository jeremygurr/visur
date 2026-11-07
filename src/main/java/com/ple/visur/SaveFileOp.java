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
    boolean toldWhere = targetPath != null && !targetPath.isEmpty();
    if (!toldWhere && emc.document().isReadOnly()) {
      // The window said it was only looking, and that answer was about this file. A path typed out is a different
      // statement, so `:w elsewhere` still works: the way out of a read-only file is to say which file you mean.
      emc.session().reportError("read-only: :w a path writes somewhere else");
      return;
    }
    if (!toldWhere) {
      filePath = emc.document().getSourceFilePath();
    }
    if (filePath == null || filePath.isEmpty()) {
      // The wording the map asked for, quoted exactly: someone who hits this searches for the string.
      emc.session().reportError("Error writing: No filename provided");
      return;
    }

    Path tempFile = null;
    try {
      Path target = Paths.get(filePath);
      // Absolute first: a bare name like `notes.txt` has no parent to write its temp file beside, and the working
      // directory is where vim would have put it.
      Path parent = target.toAbsolutePath().getParent();
      if (parent != null) {
        Files.createDirectories(parent);
      }
      // Atomic write: temp file + rename.
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
      // The text is in a file now, so the swap that said otherwise goes: first the one being made false, then the name
      // change (which is also a reason to put a swap down, and deliberately never touches a swap belonging to
      // somebody else's crash on the new name).
      emc.document().deleteSwap();
      emc.document().setSourceFilePath(filePath);
      // The text in memory is now the text in that file, so the mode line stops saying otherwise. Only here: the
      // catch below reports and returns, and a save that failed has not made anything safe.
      emc.document().markSaved();
      // Text that has been written is not being looked at any more, whichever file it went to: the read-only answer
      // was about not making a file worse, and this one just got the text.
      emc.document().putReadOnly(false);
    } catch (IOException e) {
      if (tempFile != null) {
        try { Files.deleteIfExists(tempFile); } catch (IOException ignored) {}
      }
      emc.session().reportError("saveFile: " + e.getMessage());
    }
  }
}
