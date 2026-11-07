package com.ple.visur;

import io.vertx.rxjava3.core.Vertx;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Stack;

import static org.junit.jupiter.api.Assertions.*;

/**
 * What visur does when someone opens a file that a swap says is not the whole story.
 *
 * <p>This is the first question visur has ever asked, and the situation is vim's: somebody — possibly you, possibly a
 * crash — has unsaved work in this file, and the bytes on disk disagree with the swap about what the file is. There is
 * no safe answer, so the user gives it, and the three answers are the three things that can be true.
 *
 * <p>Typed at the same seam as {@link CommandLineTest}: {@code :open}, then one key. The offer is raised by a command
 * and answered by a keypress, and a test of the operator alone would miss both halves.
 */
class RecoveryOfferTest {
  private static final String ON_DISK = "what the file has\n";
  private static final String IN_THE_SWAP = "what only the swap has\n";

  private Vertx vertx;
  private EditorModelCoupler emc;

  @TempDir
  Path tempDir;

  @BeforeEach
  void setUp() {
    vertx = Vertx.vertx();
    ServiceHolder.init();
    ServiceHolder.initializerService.initializeEditorModel();
    emc = ServiceHolder.editorModelCoupler;
    emc.document().setSourceFilePath("");
    emc.document().putContentAsSaved("nothing on screen yet\n");
    emc.session().putCanvasWidth(80);
    emc.session().putCanvasHeight(24);
    emc.session().putCanvasStart(0);
    emc.session().putCA(0);
    Stack<EditorSubmode> submodes = new Stack<>();
    submodes.push(EditorSubmode.navigate);
    emc.session().putEditorSubmodeStack(submodes);
  }

  @AfterEach
  void tearDown() {
    vertx.close();
    System.clearProperty("visur.swapDir");
  }

  /** A file with work in its swap, as it would be found after a crash. */
  private Path crashedFile() throws IOException {
    Path file = tempDir.resolve("crashed.txt");
    Files.writeString(file, ON_DISK);
    Files.writeString(tempDir.resolve(".crashed.txt.swp"), IN_THE_SWAP);
    return file;
  }

  private void typeCommand(String line) {
    press(":");
    for (String key : line.split("")) {
      press(key);
    }
    press("Enter");
  }

  private void press(String key) {
    KeyWasPressedVerticle.press(emc, KeyPressed.from(key), key);
  }

  private String message() {
    return View.of(emc).render().getString("message");
  }

  private String content() {
    return emc.document().getContent();
  }

  /**
   * The open stops rather than opening and complaining afterwards. A user who is told after the fact that the buffer
   * holds one of two things no longer has the other one, and the offer is worth nothing if answering it means undoing
   * something that already happened.
   */
  @Test
  void openingAFileWhoseSwapHasWorkOffersThreeAnswersAndOpensNothing() throws IOException {
    Path file = crashedFile();

    typeCommand("open " + file);

    assertTrue(message().contains("crashed.txt"), "it names the file it refused to open: " + message());
    assertTrue(message().contains("recover") && message().contains("delete") && message().contains("read-only"),
      "three answers, said: " + message());
    assertEquals("nothing on screen yet\n", content(), "the open is waiting on the answer, not the other way round");
    assertFalse(emc.document().isDirty(), "refusing to open something is not an edit");
    assertTrue(Files.exists(tempDir.resolve(".crashed.txt.swp")), "the question has not consumed the evidence");
  }

  /**
   * Recover: the swap's text comes into the buffer, and it comes in *unsaved* — that is the entire reason a swap
   * exists. The file on disk is not touched by an answer that says nothing about it.
   */
  @Test
  void recoveringTakesTheSwapsWorkAndKeepsItUnsaved() throws IOException {
    Path file = crashedFile();
    typeCommand("open " + file);

    press("r");

    assertEquals(IN_THE_SWAP, content());
    assertEquals(file.toString(), emc.document().getSourceFilePath(), "the recovered work belongs to that file");
    assertTrue(emc.document().isDirty(), "a swap is a record of text that never arrived, and recovering it has not "
      + "changed that");
    assertEquals(ON_DISK, Files.readString(file), "recovering is not saving");
  }

  /**
   * Delete: the work is rejected, and rejecting it means the swap goes too. A swap left behind would ask the same
   * question of the next person to open this file, who would answer it without any idea that somebody already said no.
   */
  @Test
  void deletingTheSwapOpensTheFileClean() throws IOException {
    Path file = crashedFile();
    typeCommand("open " + file);

    press("d");

    assertFalse(Files.exists(tempDir.resolve(".crashed.txt.swp")), "the answer was that this work does not exist");
    assertEquals(ON_DISK, content());
    assertFalse(emc.document().isDirty());
  }

  /**
   * Read-only: look, and do not make it worse. This is the answer for someone who is not sure, which is why it opens
   * the file at all — the other two choose a version, this one defers, and deferring has to be possible without
   * overwriting what a crash left behind.
   */
  @Test
  void readOnlyOpensTheFileAndRefusesToWriteIt() throws IOException {
    Path file = crashedFile();
    typeCommand("open " + file);
    press("o");

    assertEquals(ON_DISK, content());
    assertTrue(emc.document().isReadOnly(), "the window said it was only looking");
    assertTrue(Files.exists(tempDir.resolve(".crashed.txt.swp")), "answering a question is not a reason to destroy an"
      + " answer someone else might need");

    press("i");
    press("x");
    press("Escape");
    typeCommand("w");

    assertTrue(message().contains("read-only"), "and it says so, rather than writing: " + message());
    assertEquals(ON_DISK, Files.readString(file), "the file is what it was");
  }

  /** The way out of a question you did not expect: `:w path` is an explicit statement about a file, so it writes. */
  @Test
  void readOnlyGivesWayToAPathTypedOnPurpose() throws IOException {
    Path file = crashedFile();
    typeCommand("open " + file);
    press("o");
    press("i");
    press("!");
    press("Escape");
    Path elsewhere = tempDir.resolve("elsewhere.txt");

    typeCommand("w " + elsewhere);

    assertEquals(emc.document().getContent(), Files.readString(elsewhere),
      "an explicit path is an explicit decision about a file, which is more than a flag said");
    assertFalse(emc.document().isReadOnly(), "the flag was about the file that crashed, not about the one being saved");
    assertEquals(ON_DISK, Files.readString(file), "and the file that was being looked at stays as it was");
  }

  /**
   * Escape, and every key that is not one of the three: nothing whatever happens. The safe answer has to be reachable
   * by accident, because the question appears in front of someone who was not expecting it, and a stray key must not
   * choose a version of a file.
   */
  @Test
  void anyOtherAnswerChangesNothingAtAll() throws IOException {
    Path file = crashedFile();
    typeCommand("open " + file);

    press("Escape");

    assertEquals("nothing on screen yet\n", content(), "the file stays closed and the buffer stays as it was");
    assertTrue(Files.exists(tempDir.resolve(".crashed.txt.swp")));
    assertFalse(emc.document().isDirty());
    assertNull(emc.session().getPendingOffer(), "the question was answered, even by a key that chose nothing");
    typeCommand("open " + file);
    assertTrue(message().contains("recover"), "and asking again works, because nothing consumed the offer");
  }
}
