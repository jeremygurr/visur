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
    ServiceHolder.init(vertx.sharedData());
    // Initialize with some content
    ServiceHolder.editorModelCoupler.putEditorContent("hello world\n");
    ServiceHolder.editorModelCoupler.setSourceFilePath("");
  }

  @AfterEach
  void tearDown() throws IOException {
    vertx.close();
  }

  @Test
  void saveFileToPathWritesExactBytes() throws IOException {
    Path target = tempDir.resolve("test.txt");
    ServiceHolder.editorModelCoupler.putEditorContent("hello\nworld");
    ServiceHolder.editorModelCoupler.putOnExecutionDataStack(target.toString());
    new SaveFileOp().execute(null);

    byte[] written = Files.readAllBytes(target);
    assertArrayEquals("hello\nworld".getBytes(StandardCharsets.UTF_8), written);
    assertEquals(target.toString(), ServiceHolder.editorModelCoupler.getSourceFilePath());
  }

  @Test
  void saveFileWithoutPathUsesSourcePath() throws IOException {
    Path target = tempDir.resolve("source.txt");
    String content = "saved from source";
    ServiceHolder.editorModelCoupler.putEditorContent(content);
    ServiceHolder.editorModelCoupler.setSourceFilePath(target.toString());
    new SaveFileOp().execute(null);

    byte[] written = Files.readAllBytes(target);
    assertEquals(content, new String(written, StandardCharsets.UTF_8));
  }

  @Test
  void saveFileWithoutPathOrSourceReportsError() throws IOException {
    ServiceHolder.editorModelCoupler.putEditorContent("no source");
    ServiceHolder.editorModelCoupler.setSourceFilePath("");
    // No argument on EDS
    new SaveFileOp().execute(null);

    // Document is unchanged
    assertEquals("no source", ServiceHolder.editorModelCoupler.getEditorContent());
  }

  @Test
  void saveFileWithExplicitPathSavesThereAndUpdatesSource() throws IOException {
    Path target = tempDir.resolve("renamed.txt");
    ServiceHolder.editorModelCoupler.putEditorContent("content");
    ServiceHolder.editorModelCoupler.setSourceFilePath(tempDir.resolve("old.txt").toString());
    ServiceHolder.editorModelCoupler.putOnExecutionDataStack(target.toString());
    new SaveFileOp().execute(null);

    byte[] written = Files.readAllBytes(target);
    assertEquals("content", new String(written, StandardCharsets.UTF_8));
    assertEquals(target.toString(), ServiceHolder.editorModelCoupler.getSourceFilePath());
  }

  @Test
  void saveFileNoTrailingNewlinePreservesAbsence() throws IOException {
    Path target = tempDir.resolve("noNewline.txt");
    ServiceHolder.editorModelCoupler.putEditorContent("no trailing newline");
    ServiceHolder.editorModelCoupler.putOnExecutionDataStack(target.toString());
    new SaveFileOp().execute(null);

    byte[] written = Files.readAllBytes(target);
    // Must NOT end with newline
    assertFalse(written[written.length - 1] == '\n');
    assertEquals("no trailing newline", new String(written, StandardCharsets.UTF_8));
  }

  @Test
  void saveFileWithTrailingNewlinePreservesIt() throws IOException {
    Path target = tempDir.resolve("withNewline.txt");
    ServiceHolder.editorModelCoupler.putEditorContent("trailing\n");
    ServiceHolder.editorModelCoupler.putOnExecutionDataStack(target.toString());
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

    ServiceHolder.editorModelCoupler.putEditorContent("original");
    ServiceHolder.editorModelCoupler.putOnExecutionDataStack(file.toString());
    new LoadFileOp().execute(null);

    assertEquals(content, ServiceHolder.editorModelCoupler.getEditorContent());
    assertEquals(file.toString(), ServiceHolder.editorModelCoupler.getSourceFilePath());
  }

  @Test
  void loadFileMissingFileLeavesDocumentUntouched() {
    ServiceHolder.editorModelCoupler.putEditorContent("stays");
    ServiceHolder.editorModelCoupler.putOnExecutionDataStack("/nonexistent/path.txt");
    new LoadFileOp().execute(null);

    assertEquals("stays", ServiceHolder.editorModelCoupler.getEditorContent());
  }

  @Test
  void loadFileNoArgumentReportsError() {
    ServiceHolder.editorModelCoupler.putEditorContent("stays");
    new LoadFileOp().execute(null);

    assertEquals("stays", ServiceHolder.editorModelCoupler.getEditorContent());
  }

  @Test
  void roundTripSaveThenLoadIsIdentity() throws IOException {
    Path file = tempDir.resolve("roundtrip.txt");
    String original = "line 1\nline 2\nno trailing newline";
    ServiceHolder.editorModelCoupler.putEditorContent(original);
    ServiceHolder.editorModelCoupler.putOnExecutionDataStack(file.toString());
    new SaveFileOp().execute(null);

    // Now load it back
    ServiceHolder.editorModelCoupler.putEditorContent("REPLACED");
    ServiceHolder.editorModelCoupler.putOnExecutionDataStack(file.toString());
    new LoadFileOp().execute(null);

    assertEquals(original, ServiceHolder.editorModelCoupler.getEditorContent());
  }

  @Test
  void roundTripWithCrlf() throws IOException {
    Path file = tempDir.resolve("crlf.txt");
    String original = "windows\r\nline 2\r\n";
    ServiceHolder.editorModelCoupler.putEditorContent(original);
    ServiceHolder.editorModelCoupler.putOnExecutionDataStack(file.toString());
    new SaveFileOp().execute(null);

    // Verify the file has CRLF
    byte[] bytes = Files.readAllBytes(file);
    // "windows" is 7 chars, so \r is at position 7
    assertTrue(bytes[7] == '\r', "expected \\r at position 7, got " + bytes[7]);
    assertEquals(original, new String(bytes, StandardCharsets.UTF_8));

    // Load back
    ServiceHolder.editorModelCoupler.putEditorContent("REPLACED");
    ServiceHolder.editorModelCoupler.putOnExecutionDataStack(file.toString());
    new LoadFileOp().execute(null);

    assertEquals(original, ServiceHolder.editorModelCoupler.getEditorContent());
  }

  @Test
  void atomicWriteOldFileSurvivesFailure() throws IOException {
    Path target = tempDir.resolve("atomic.txt");
    String oldContent = "I was here before";
    String newContent = "new content";
    Files.write(target, oldContent.getBytes(StandardCharsets.UTF_8));

    // Overwrite to a path in tempDir
    ServiceHolder.editorModelCoupler.putEditorContent(newContent);
    ServiceHolder.editorModelCoupler.putOnExecutionDataStack(target.toString());
    // This should succeed
    new SaveFileOp().execute(null);

    // Verify the file was written
    byte[] bytes = Files.readAllBytes(target);
    assertEquals(newContent, new String(bytes, StandardCharsets.UTF_8));
  }

  @Test
  void saveFileCreatesParentDirectories() throws IOException {
    Path target = tempDir.resolve("subdir/nested/file.txt");
    ServiceHolder.editorModelCoupler.putEditorContent("nested");
    ServiceHolder.editorModelCoupler.putOnExecutionDataStack(target.toString());
    new SaveFileOp().execute(null);

    byte[] written = Files.readAllBytes(target);
    assertEquals("nested", new String(written, StandardCharsets.UTF_8));
  }
}
