package com.ple.visur;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * {@code :q!} — leave, and throw away what was never saved.
 *
 * <p>The swap file goes with the work, and leaving it behind is a bug with a delay attached: the swap is what the next
 * boot loads instead of the file, so discarding text in one session would resurrect it in the next. The file being
 * edited is not touched — discarding the buffer says nothing about what is on disk, and only a save ever speaks for that.
 *
 * <p>The path is the disk's name for the swap, spelled out here as it is in two other places; ticket 06 replaces this
 * one-swap-per-document scheme with a nameless pool, and that is when the spelling gets one owner.
 */
public class QuitDiscardingOp implements Operator {
  @Override
  public void execute(Object opInfo) {
    EditorModelCoupler emc = ServiceHolder.editorModelCoupler;
    String source = emc.document().getSourceFilePath();
    if(source != null && !source.isEmpty()) {
      Path swap = Paths.get(source + ".visur-swap");
      try {
        Files.deleteIfExists(swap);
      } catch (IOException failure) {
        // Worth a message and not worth refusing the quit for: the window is leaving either way, and the text it was
        // discarding is out of memory regardless of what the disk says about its copy.
        emc.session().reportError(":q! could not delete the swap file: " + failure.getMessage());
      }
    }
    emc.session().putQuitWasRequested(true);
  }
}
