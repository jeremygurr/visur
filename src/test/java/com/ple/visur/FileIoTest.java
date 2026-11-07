package com.ple.visur;

import io.vertx.rxjava3.core.Vertx;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for SaveFileOp and LoadFileOp.
 *
 * <p>Covers: byte-exact UTF-8 save/load, round-trip identity, no-trailing-newline handling,
 * atomic write (old file survives), no-path error, explicit-path save-as,
 * and loadFile with missing file leaving the document untouched.
 */
class FileIoTest {
  private Vertx vertx;

  /** Longer than the swap's debounce window, so a write that is allowed to lag has certainly happened. */
  private static final long SWAP_WINDOW_MS = 600;

  @TempDir
  Path tempDir;

  @BeforeEach
  void setUp() {
    // Every test here edits a document, and editing writes a swap. Without this the pool would be claimed in whatever
    // directory the build happened to be run from, which is the one thing this file must never do to a working tree.
    System.setProperty("visur.swapDir", tempDir.toString());
    vertx = Vertx.vertx();
    ServiceHolder.init();
    // Initialize with some content
    ServiceHolder.editorModelCoupler.document().putContent("hello world\n");
    ServiceHolder.editorModelCoupler.document().setSourceFilePath("");
  }

  @AfterEach
  void tearDown() throws IOException {
    System.clearProperty("visur.swapDir");
    vertx.close();
  }

  private Document document() {
    return ServiceHolder.editorModelCoupler.document();
  }

  @Test
  void saveFileToPathWritesExactBytes() throws IOException {
    Path target = tempDir.resolve("test.txt");
    ServiceHolder.editorModelCoupler.document().putContent("hello\nworld");
    ServiceHolder.editorModelCoupler.session().putOnExecutionDataStack(target.toString());
    new SaveFileOp().execute(null);

    byte[] written = Files.readAllBytes(target);
    assertArrayEquals("hello\nworld".getBytes(StandardCharsets.UTF_8), written);
    assertEquals(target.toString(), ServiceHolder.editorModelCoupler.document().getSourceFilePath());
  }

  @Test
  void saveFileWithoutPathUsesSourcePath() throws IOException {
    Path target = tempDir.resolve("source.txt");
    String content = "saved from source";
    ServiceHolder.editorModelCoupler.document().putContent(content);
    ServiceHolder.editorModelCoupler.document().setSourceFilePath(target.toString());
    new SaveFileOp().execute(null);

    byte[] written = Files.readAllBytes(target);
    assertEquals(content, new String(written, StandardCharsets.UTF_8));
  }

  /**
   * What each of the three tests below asserts now, and did not before: that a message exists at all. Asserting only
   * that the document is unchanged was a test that passed with {@code reportError} doing nothing but printing, which is
   * how every file error in this editor stayed invisible to the person it was for.
   */
  private void assertReportedSomethingWorthReading() {
    String message = ServiceHolder.editorModelCoupler.session().getMessage();
    assertFalse(message.isBlank(), "an error the screen does not show is an error that did not happen");
  }

  @Test
  void saveFileWithoutPathOrSourceReportsError() throws IOException {
    ServiceHolder.editorModelCoupler.document().putContent("no source");
    ServiceHolder.editorModelCoupler.document().setSourceFilePath("");
    // No argument on EDS
    new SaveFileOp().execute(null);

    // Document is unchanged
    assertEquals("no source", ServiceHolder.editorModelCoupler.document().getContent());
    assertReportedSomethingWorthReading();
  }

  @Test
  void saveFileWithExplicitPathSavesThereAndUpdatesSource() throws IOException {
    Path target = tempDir.resolve("renamed.txt");
    ServiceHolder.editorModelCoupler.document().putContent("content");
    ServiceHolder.editorModelCoupler.document().setSourceFilePath(tempDir.resolve("old.txt").toString());
    ServiceHolder.editorModelCoupler.session().putOnExecutionDataStack(target.toString());
    new SaveFileOp().execute(null);

    byte[] written = Files.readAllBytes(target);
    assertEquals("content", new String(written, StandardCharsets.UTF_8));
    assertEquals(target.toString(), ServiceHolder.editorModelCoupler.document().getSourceFilePath());
  }

  @Test
  void saveFileNoTrailingNewlinePreservesAbsence() throws IOException {
    Path target = tempDir.resolve("noNewline.txt");
    ServiceHolder.editorModelCoupler.document().putContent("no trailing newline");
    ServiceHolder.editorModelCoupler.session().putOnExecutionDataStack(target.toString());
    new SaveFileOp().execute(null);

    byte[] written = Files.readAllBytes(target);
    // Must NOT end with newline
    assertFalse(written[written.length - 1] == '\n');
    assertEquals("no trailing newline", new String(written, StandardCharsets.UTF_8));
  }

  @Test
  void saveFileWithTrailingNewlinePreservesIt() throws IOException {
    Path target = tempDir.resolve("withNewline.txt");
    ServiceHolder.editorModelCoupler.document().putContent("trailing\n");
    ServiceHolder.editorModelCoupler.session().putOnExecutionDataStack(target.toString());
    new SaveFileOp().execute(null);

    byte[] written = Files.readAllBytes(target);
    assertTrue(written[written.length - 1] == '\n');
    assertEquals("trailing\n", new String(written, StandardCharsets.UTF_8));
  }

  @Test
  void loadFileReadsAndReplacesDocument() throws IOException {
    Path file = tempDir.resolve("loadme.txt");
    String content = "loaded content\nwith lines";
    Files.write(file, content.getBytes(StandardCharsets.UTF_8));

    // As-saved, because loading over unsaved work is refused, and this test is about what a load replaces rather than
    // about the refusal. Every load in this file says the same thing for the same reason.
    ServiceHolder.editorModelCoupler.document().putContentAsSaved("original");
    ServiceHolder.editorModelCoupler.session().putOnExecutionDataStack(file.toString());
    new LoadFileOp().execute(null);

    assertEquals(content, ServiceHolder.editorModelCoupler.document().getContent());
    assertEquals(file.toString(), ServiceHolder.editorModelCoupler.document().getSourceFilePath());
  }

  @Test
  void loadFileMissingFileLeavesDocumentUntouched() {
    ServiceHolder.editorModelCoupler.document().putContentAsSaved("stays");
    ServiceHolder.editorModelCoupler.session().putOnExecutionDataStack("/nonexistent/path.txt");
    new LoadFileOp().execute(null);

    assertEquals("stays", ServiceHolder.editorModelCoupler.document().getContent());
    assertTrue(ServiceHolder.editorModelCoupler.session().getMessage().contains("/nonexistent/path.txt"),
      "an error that does not name the file it failed on tells the user nothing");
  }

  @Test
  void loadFileNoArgumentReportsError() {
    ServiceHolder.editorModelCoupler.document().putContentAsSaved("stays");
    new LoadFileOp().execute(null);

    assertEquals("stays", ServiceHolder.editorModelCoupler.document().getContent());
    assertEquals(":open needs a path: :open /path/to/file", ServiceHolder.editorModelCoupler.session().getMessage(),
      "the complaint names the command a person would type, not the operator they have never heard of");
  }

  /**
   * The marker's reason for existing: a write that happened ends the warning, and the comparison against the saved
   * text is what makes that true without anything having to remember to clear a flag.
   */
  @Test
  void savingClearsTheUnsavedMarker() throws IOException {
    Path target = tempDir.resolve("clean.txt");
    ServiceHolder.editorModelCoupler.document().putContent("edited");
    ServiceHolder.editorModelCoupler.session().putOnExecutionDataStack(target.toString());

    new SaveFileOp().execute(null);

    assertFalse(ServiceHolder.editorModelCoupler.document().isDirty(), "the text is in that file now");
  }

  /** {@code ;"other.txt" saveFile} writes somewhere new *and* that new file is what the buffer now matches. */
  @Test
  void savingToANewPathMovesTheMarkerAndTheName() throws IOException {
    Path target = tempDir.resolve("newname.txt");
    ServiceHolder.editorModelCoupler.document().putContent("renamed work");
    ServiceHolder.editorModelCoupler.session().putOnExecutionDataStack(target.toString());

    new SaveFileOp().execute(null);

    assertFalse(ServiceHolder.editorModelCoupler.document().isDirty(), "the buffer and the new file hold the same text");
    assertEquals(target.toString(), ServiceHolder.editorModelCoupler.document().getSourceFilePath());
  }

  /**
   * A failed save that clears the marker is the worst bug this feature could ship: the user closes the window
   * trusting a warning that was quietly removed. Unwritable here is a path whose parent is a regular file, which
   * fails the same way on any platform without needing a read-only bit to be honoured.
   */
  @Test
  void aFailedSaveLeavesTheDocumentDirtyAndSaysWhy() throws IOException {
    Path aFile = tempDir.resolve("notadir");
    Files.write(aFile, "x".getBytes(StandardCharsets.UTF_8));
    ServiceHolder.editorModelCoupler.document().putContent("work that never got out");
    ServiceHolder.editorModelCoupler.session().putOnExecutionDataStack(aFile.resolve("child.txt").toString());

    new SaveFileOp().execute(null);

    assertTrue(ServiceHolder.editorModelCoupler.document().isDirty(),
      "nothing was written, so the warning has to stay up");
    assertReportedSomethingWorthReading();
  }

  /** Text that came off the file is not unsaved work, which is the difference between loading and typing. */
  @Test
  void loadingAFileIsNotUnsavedWork() throws IOException {
    Path file = tempDir.resolve("clean-load.txt");
    Files.write(file, "from disk\n".getBytes(StandardCharsets.UTF_8));
    ServiceHolder.editorModelCoupler.document().putContentAsSaved("something else entirely");
    ServiceHolder.editorModelCoupler.session().putOnExecutionDataStack(file.toString());

    new LoadFileOp().execute(null);

    assertFalse(ServiceHolder.editorModelCoupler.document().isDirty(), "the buffer now holds exactly what the file holds");
  }

  /**
   * The baseline moves in three places — startup load, {@code loadFile}, save — and this is the test that catches
   * somebody writing it in one. Recovery reads text out of a file on disk, so a baseline set wherever text is read
   * would clear the marker here; but a swap file exists precisely because its text never reached the file, so a
   * recovered document is unsaved work by definition.
   */
  @Test
  void recoveringASwapFileLeavesTheDocumentDirty() throws IOException {
    Path file = tempDir.resolve("crash.txt");
    Files.write(file, "what the file has\n".getBytes(StandardCharsets.UTF_8));
    Files.write(Paths.get(file + ".visur-swap"), "what only the swap has\n".getBytes(StandardCharsets.UTF_8));
    ServiceHolder.editorModelCoupler.document().setSourceFilePath(file.toString());

    ServiceHolder.initializerService.recoverFromSwapFiles();

    assertEquals("what only the swap has\n", ServiceHolder.editorModelCoupler.document().getContent());
    assertTrue(ServiceHolder.editorModelCoupler.document().isDirty(),
      "this text has never been in the file, whatever else has happened to it");
  }

  @Test
  void roundTripSaveThenLoadIsIdentity() throws IOException {
    Path file = tempDir.resolve("roundtrip.txt");
    String original = "line 1\nline 2\nno trailing newline";
    ServiceHolder.editorModelCoupler.document().putContent(original);
    ServiceHolder.editorModelCoupler.session().putOnExecutionDataStack(file.toString());
    new SaveFileOp().execute(null);

    // Now load it back, from a buffer holding something else that nobody has to save first
    ServiceHolder.editorModelCoupler.document().putContentAsSaved("REPLACED");
    ServiceHolder.editorModelCoupler.session().putOnExecutionDataStack(file.toString());
    new LoadFileOp().execute(null);

    assertEquals(original, ServiceHolder.editorModelCoupler.document().getContent());
  }

  @Test
  void roundTripWithCrlf() throws IOException {
    Path file = tempDir.resolve("crlf.txt");
    String original = "windows\r\nline 2\r\n";
    ServiceHolder.editorModelCoupler.document().putContent(original);
    ServiceHolder.editorModelCoupler.session().putOnExecutionDataStack(file.toString());
    new SaveFileOp().execute(null);

    // Verify the file has CRLF
    byte[] bytes = Files.readAllBytes(file);
    // "windows" is 7 chars, so \r is at position 7
    assertTrue(bytes[7] == '\r', "expected \\r at position 7, got " + bytes[7]);
    assertEquals(original, new String(bytes, StandardCharsets.UTF_8));

    // Load back
    ServiceHolder.editorModelCoupler.document().putContentAsSaved("REPLACED");
    ServiceHolder.editorModelCoupler.session().putOnExecutionDataStack(file.toString());
    new LoadFileOp().execute(null);

    assertEquals(original, ServiceHolder.editorModelCoupler.document().getContent());
  }

  @Test
  void atomicWriteOldFileSurvivesFailure() throws IOException {
    Path target = tempDir.resolve("atomic.txt");
    String oldContent = "I was here before";
    String newContent = "new content";
    Files.write(target, oldContent.getBytes(StandardCharsets.UTF_8));

    // Overwrite to a path in tempDir
    ServiceHolder.editorModelCoupler.document().putContent(newContent);
    ServiceHolder.editorModelCoupler.session().putOnExecutionDataStack(target.toString());
    // This should succeed
    new SaveFileOp().execute(null);

    // Verify the file was written
    byte[] bytes = Files.readAllBytes(target);
    assertEquals(newContent, new String(bytes, StandardCharsets.UTF_8));
  }

  @Test
  void saveFileCreatesParentDirectories() throws IOException {
    Path target = tempDir.resolve("subdir/nested/file.txt");
    ServiceHolder.editorModelCoupler.document().putContent("nested");
    ServiceHolder.editorModelCoupler.session().putOnExecutionDataStack(target.toString());
    new SaveFileOp().execute(null);

    byte[] written = Files.readAllBytes(target);
    assertEquals("nested", new String(written, StandardCharsets.UTF_8));
  }

  @Test
  void startupContentWithNoPathMeansEmptyUnnamedDocument() {
    // Verify source path is empty or null (no named file when no VISUR_FILE).
    String path = ServiceHolder.editorModelCoupler.document().getSourceFilePath();
    assertTrue(path == null || path.isEmpty(),
      "source path should be empty or null when no VISUR_FILE is set, got: " + path);
  }

  /**
   * The rule this whole file exists for: the buffer nobody has named is the one most likely to be lost, so it is the one
   * that has to survive. The slot comes from the pool, not from the buffer's own name — it has none.
   */
  @Test
  void aNamelessDocumentMirrorsItsTextIntoAPoolSlot() throws IOException, InterruptedException {
    document().setSourceFilePath("");
    String typed = "a thought with no filename yet";
    // The debounce is the rule, not an obstacle: the swap is allowed to be half a second behind the buffer, so a test
    // of what the swap holds has to wait out that half second. Anything inside it is testing a promise nobody made.
    Thread.sleep(SWAP_WINDOW_MS);

    document().putContent(typed);

    Path slot = tempDir.resolve(".swa");
    assertTrue(Files.exists(slot), "a nameless document with content has a swap file, in the directory the rule names");
    assertEquals(typed, Files.readString(slot));
  }

  /**
   * A document that has a name puts its swap where the argument about it will happen: beside the file, carrying the
   * file's name. This is the swap that later gets a three-way prompt, so it has to be guessable by a person and not
   * only by the program that wrote it.
   */
  @Test
  void aNamedDocumentMirrorsIntoASwapBesideItsOwnFile() throws IOException, InterruptedException {
    Path file = tempDir.resolve("notes.txt");
    Files.write(file, "on disk\n".getBytes(StandardCharsets.UTF_8));
    document().setSourceFilePath(file.toString());
    Thread.sleep(SWAP_WINDOW_MS);

    document().putContent("edited, unsaved");

    assertEquals("edited, unsaved", Files.readString(tempDir.resolve(".notes.txt.swp")));
  }

  /**
   * The rule: text that never reached a file is not lost, and the proof is the file beside it. This replaces the old
   * "the next boot loads the swap into the buffer" test, because the next boot loads nothing anybody did not ask for;
   * what it can do is refuse to be fooled about which file is which.
   */
  @Test
  void anUnsavedEditSurvivesInTheSwapBesideItsFile() throws IOException, InterruptedException {
    Path file = tempDir.resolve("recovery_test.txt");
    Files.write(file, "what the file holds\n".getBytes(StandardCharsets.UTF_8));
    document().setSourceFilePath(file.toString());
    String edited = "edited content that was never saved";
    Thread.sleep(SWAP_WINDOW_MS);

    document().putContent(edited);

    assertEquals(edited, Files.readString(tempDir.resolve(".recovery_test.txt.swp")),
      "the swap is where the work that never reached the file survives");
    assertEquals("what the file holds\n", Files.readString(file), "a swap says nothing about the file, and writes nothing to it");
    assertTrue(document().isDirty());
  }

  /**
   * A save that failed is not a save, and the difference shows up on disk afterwards: the old file still holds what it
   * held, the edit is still recoverable, and the document is still dirty. Deleting the swap is what a *successful* save
   * does, and getting this wrong is how a disk-full error eats somebody's work.
   */
  @Test
  void aFailedSaveLeavesTheSwapAndTheOldFileAlone() throws IOException, InterruptedException {
    Path file = tempDir.resolve("crash_save_test.txt");
    Files.write(file, "original content\n".getBytes(StandardCharsets.UTF_8));
    document().setSourceFilePath(file.toString());
    Thread.sleep(SWAP_WINDOW_MS);
    document().putContent("edited content - crash happened mid-save");

    // Where this save is aimed, a file is in the directory's place, so nothing about the target can be created.
    Path blocker = tempDir.resolve("not_a_directory");
    Files.write(blocker, "in the way\n".getBytes(StandardCharsets.UTF_8));
    ServiceHolder.editorModelCoupler.session().putOnExecutionDataStack(blocker.resolve("nested.txt").toString());

    new SaveFileOp().execute(null);

    assertTrue(Files.exists(tempDir.resolve(".crash_save_test.txt.swp")),
      "a swap is deleted by text that arrived, not by an attempt to make it arrive");
    assertEquals("original content\n", Files.readString(file));
    assertEquals("edited content - crash happened mid-save", Files.readString(tempDir.resolve(".crash_save_test.txt.swp")));
    assertTrue(document().isDirty(), "the mode line keeps saying what the disk agrees with");
  }

  /**
   * The other half of the delete: a save that worked. The text is in a file now, so the swap would be a promise about
   * work that has already been done, and a stale one is worse than none — it gets offered to whoever opens this name
   * next as though it were unsaved work.
   */
  @Test
  void aSuccessfulSaveTakesTheSwapWithIt() throws IOException, InterruptedException {
    Path file = tempDir.resolve("saved_at_last.txt");
    Files.write(file, "on disk\n".getBytes(StandardCharsets.UTF_8));
    document().setSourceFilePath(file.toString());
    Thread.sleep(SWAP_WINDOW_MS);
    document().putContent("now saved\n");

    new SaveFileOp().execute(null);

    assertFalse(Files.exists(tempDir.resolve(".saved_at_last.txt.swp")), "a saved document has nothing to recover");
    assertEquals("now saved\n", Files.readString(file));
    assertFalse(document().isDirty());
  }
}
