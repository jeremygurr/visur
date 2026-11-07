package com.ple.visur;

import java.io.File;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Where a swap file lives, and which one a document gets.
 *
 * <p>Two kinds, because they answer different questions. A document with a name keeps its swap beside the file it came
 * from, under a name anyone can guess, because that is where the argument "the file on disk is not what you left" gets
 * made. A document with no name has no such anchor, so it takes a slot from a pool in a directory visur decides, and
 * the slot has to outlive the crash and be findable by somebody who has forgotten the buffer existed.
 *
 * <p>One property, {@code visur.swapDir}, replaces the pool directory. It exists so a test can point the pool at a temp
 * directory instead of at wherever the tests happened to be run from, and it is honoured before anything else because a
 * person who sets it means it.
 */
public final class SwapFiles {

  /** The launch directory or the state directory, depending on who is typing; null when nobody has said who they are. */
  public static Path poolDir(Session session) {
    String override = System.getProperty("visur.swapDir");
    if (override != null && !override.isEmpty()) {
      return Path.of(override);
    }
    ClientKind kind = session.getClientKind();
    if (kind == ClientKind.TERMINAL) {
      return Path.of(System.getProperty("user.dir"));
    }
    if (kind == ClientKind.BROWSER) {
      // A browser tab has no project to be unsaved inside of, so its names belong to visur rather than to a repo.
      return Path.of(System.getProperty("user.home"), ".local", "state", "visur", "browser");
    }
    return null;
  }

  /**
   * A slot nobody else holds, created empty so that holding it and existing are the same fact.
   *
   * <p>Create-exclusive and try the next letter is the whole concurrency story: two browser sessions reaching for the
   * pool at the same instant cannot both be told they got {@code .swa}, and there is no lock to forget to release.
   */
  public static Path claimSlot(Path dir) throws java.io.IOException {
    Files.createDirectories(dir);
    for (int round = 0; ; round++) {
      for (char letter = 'a'; letter <= 'z'; letter++) {
        Path slot = dir.resolve(".sw" + letter + (round == 0 ? "" : Integer.toString(round)));
        try {
          Files.createFile(slot);
          return slot;
        } catch (FileAlreadyExistsException taken) {
          // Somebody else's buffer, or one that crashed and was never cleaned up. Both mean the same thing here: not ours.
        }
      }
    }
  }

  /** The swap of a document that has a name: vim's, in the file's own directory, where a person would look. */
  public static Path beside(File file) {
    return file.toPath().resolveSibling("." + file.getName() + ".swp");
  }

  private SwapFiles() {
  }
}
