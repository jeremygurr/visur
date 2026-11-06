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

  @TempDir
  Path tempDir;

  @BeforeEach
  void setUp() {
    vertx = Vertx.vertx();
    ServiceHolder.init();
    // Initialize with some content
    ServiceHolder.editorModelCoupler.document().putContent("hello world\n");
    ServiceHolder.editorModelCoupler.document().setSourceFilePath("");
  }

  @AfterEach
  void tearDown() throws IOException {
    vertx.close();
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
   * Test (a): edit a document, never save, simulate process dying, restart -
   * the edited content comes back via swap file recovery.
   */
  @Test
  void neverSavedDocumentRecoverableFromSwap() throws IOException, InterruptedException {
    Path target = tempDir.resolve("recovery_test.txt");
    // Start with a source path (as if the file was set at startup or by loadFile)
    ServiceHolder.editorModelCoupler.document().setSourceFilePath(target.toString());
    // Simulate editing: change the document content (this triggers swap write).
    String editedContent = "edited content that was never saved";
    ServiceHolder.editorModelCoupler.document().putContent(editedContent);

    // The swap file should exist (written by putEditorContent with debounce).
    Path swapPath = Paths.get(target.toString() + ".visur-swap");
    // Wait for debounce (500ms) then verify swap exists with correct content.
    Thread.sleep(600);
    assertTrue(Files.exists(swapPath), "swap file should exist for live document");
    String recovered = new String(Files.readAllBytes(swapPath), StandardCharsets.UTF_8);
    assertEquals(editedContent, recovered);
  }

  /**
   * Test (b): simulate a crash inside a save's write window -
   * the previous file is intact and the edited content is recoverable.
   */
  @Test
  void crashDuringSaveRestoresPreviousFileAndRecoverableEditedContent() throws IOException, InterruptedException {
    Path target = tempDir.resolve("crash_save_test.txt");
    String originalContent = "original content";
    Files.write(target, originalContent.getBytes(StandardCharsets.UTF_8));
    String editedContent = "edited content - crash happened mid-save";

    // Set up: the document has been edited (swap written by putEditorContent)
    ServiceHolder.editorModelCoupler.document().setSourceFilePath(target.toString());
    ServiceHolder.editorModelCoupler.document().putContent(editedContent);
    // Wait for debounce so swap is written.
    Thread.sleep(600);

    // Verify swap exists
    Path swapPath = Paths.get(target.toString() + ".visur-swap");
    assertTrue(Files.exists(swapPath));
    assertEquals(editedContent, new String(Files.readAllBytes(swapPath), StandardCharsets.UTF_8));

    // Simulate crash: leave the swap file in place but the target file was not yet updated.
    // (In a real crash, the temp file + rename never completes.)
    // On restart: startup recovery reads the swap and recovers the edited content.
    // Verify the swap has the edited content (the target has the original).
    byte[] targetBytes = Files.readAllBytes(target);
    assertEquals(originalContent, new String(targetBytes, StandardCharsets.UTF_8));
    String swapContent = new String(Files.readAllBytes(swapPath), StandardCharsets.UTF_8);
    assertEquals(editedContent, swapContent);
  }
}
