package com.ple.visur;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * A file visur refused to open, because a swap says the file is not the whole story.
 *
 * <p>The situation is vim's and so are the three answers: somebody — possibly you, possibly a crash — has work in this
 * file that the bytes on disk do not have. All three answers can be wrong, which is why the open stops and waits
 * instead of opening and complaining afterwards.
 *
 * <p>It is a question and not a message: a message is gone on the next keypress and needs nothing, and this needs an
 * answer before it lets go. The text is still drawn in the message row, because that is the only line visur has for
 * telling anybody anything; what makes it a question is that the next keypress is spent on answering it.
 */
public class RecoveryOffer {

  /** What the next keypress meant. Anything that is not one of the three answers is the safe one. */
  public enum Answer {
    RECOVER,
    DELETE_SWAP,
    READ_ONLY,
    DECLINED
  }

  private final String path;
  private final Path swap;

  RecoveryOffer(String path, Path swap) {
    this.path = path;
    this.swap = swap;
  }

  /** The question, in the one line visur has. The file name comes first because it is what the tail gets clipped to. */
  public String question() {
    return Paths.get(path).getFileName() + " has unsaved work: r recover  d delete  o read-only";
  }

  static Answer answerFor(String key) {
    return switch (key) {
      case "r" -> Answer.RECOVER;
      case "d" -> Answer.DELETE_SWAP;
      case "o" -> Answer.READ_ONLY;
      default -> Answer.DECLINED;
    };
  }

  /**
   * Answer it, and open the file that the answer says is the right one.
   *
   * <p>The offer goes first, whatever the answer: an answer that fails halfway must not leave a question behind that
   * has already been paid for, and the file staying closed is what a failure looks like.
   */
  public void answer(Answer answer) {
    EditorModelCoupler emc = ServiceHolder.editorModelCoupler;
    Session window = emc.session();
    window.putPendingOffer(null);
    if (answer == Answer.DECLINED) {
      // The safe answer, and the only one that has to say nothing: the question disappearing is the feedback, and the
      // file, the buffer and the swap are all exactly where they were.
      return;
    }
    if (answer == Answer.RECOVER) {
      try {
        // Not as-saved, and that is the point of the whole feature: a swap exists because its text never reached the
        // file, so loading it in is unsaved work by definition.
        LoadFileOp.install(emc, path, Files.readString(swap, StandardCharsets.UTF_8), false);
      } catch (IOException unreadable) {
        window.reportError("recover: " + unreadable.getMessage());
      }
      return;
    }
    if (answer == Answer.DELETE_SWAP) {
      try {
        Files.deleteIfExists(swap);
      } catch (IOException stuck) {
        // Said, and then opened anyway: the work has been refused either way, and a swap the disk will not give up is
        // not a reason to keep the file away from the person who just said it was nothing.
        window.reportError(":open could not delete " + swap + ": " + stuck.getMessage());
      }
    }
    try {
      String content = Files.readString(Paths.get(path), StandardCharsets.UTF_8);
      LoadFileOp.install(emc, path, content, true);
      if (answer == Answer.READ_ONLY) {
        emc.document().putReadOnly(true);
      }
    } catch (IOException unreadable) {
      window.reportError("loadFile: " + unreadable.getMessage());
    }
  }
}
