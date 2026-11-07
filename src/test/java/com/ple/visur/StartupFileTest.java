package com.ple.visur;

import io.vertx.rxjava3.core.Vertx;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * What a booting server is allowed to do to a file it was pointed at, and to a directory it was started in.
 *
 * <p>These are the only tests that go through a real boot, because the thing under test is boot: reading the startup
 * file, and the recovery that used to happen afterwards and quietly replaced a buffer nobody had asked to have
 * replaced. Every other test in the repo initialises the model by hand, which is exactly why the old recovery survived
 * as long as it did — nothing that ran in a test ever booted.
 *
 * <p>The file a window came for is a query on its socket — {@code /ws?open=/path} — because that is what the launcher
 * sends, and it is the shape that lets a file be opened against a server that was running long before the file was
 * named. A test that boots a server is a test that connects to it.
 */
class StartupFileTest {

  private static final int PORT = 9000;

  private static int nextPort;

  @TempDir
  Path dir;

  private Vertx vertx;
  private int port;

  /** Boots a server the way the launcher does, on a port nobody else is using. */
  private void bootServer() {
    port = PORT + nextPort++;
    System.setProperty("visur.port", String.valueOf(port));
    vertx = Vertx.vertx();
    vertx.deployVerticle(new MainVerticle()).blockingGet();
  }

  @AfterEach
  void stopTheServer() {
    System.clearProperty("visur.file");
    System.clearProperty("visur.port");
    System.clearProperty("visur.swapDir");
    if (vertx != null) {
      vertx.close().blockingAwait();
    }
  }

  /** The property is the same lever as the environment variable, and this says so before anything else depends on it. */
  @Test
  void bootingWithAFileLoadsThatFile() throws IOException {
    Path file = dir.resolve("booted.txt");
    Files.writeString(file, "the file's own words\n");
    bootServer();

    try (WireClient client = new WireClient(vertx, port, 60, 8, file.toString())) {
      assertTrue(client.text().contains("the file's own words"),
        "the whole point of naming a file on the way in: " + client.text());
      assertFalse(client.view().getBoolean("isDirty"), "text that came off the file is not unsaved work");
    }
  }

  /**
   * A launch is the one place a swap has to be answered rather than ignored, because the person who typed the file name
   * is standing there. The open stops and asks, the asking is on the window that arrived, and no version of the file is
   * chosen before the answer.
   *
   * <p>The swap staying on disk is not laziness: it is the only copy of work that has not been recovered yet, and an
   * answer that consumes it has lost it if the user then quits without saving.
   */
  @Test
  void launchingOnAFileWithASwapAsksTheWindowThatArrived() throws IOException {
    Path file = dir.resolve("crashed.txt");
    Files.writeString(file, "what the file has\n");
    Path swap = dir.resolve(".crashed.txt.swp");
    Files.writeString(swap, "what only the swap has\n");
    bootServer();

    try (WireClient client = new WireClient(vertx, port, 60, 8, file.toString())) {
      assertEquals("crashed.txt has unsaved work: r recover  d delete  o read-only", client.message());
      assertFalse(client.text().contains("what the file has"), "the open waits for the answer: " + client.text());
      assertFalse(client.text().contains("what only the swap has"), "and neither version is picked on the user's behalf");

      client.presses("r");

      assertTrue(client.text().contains("what only the swap has"), "r recovers the swap's text");
      assertTrue(client.isDirty(), "a swap is text that never arrived, so recovering it is unsaved work");
    }
    assertEquals("what only the swap has\n", Files.readString(swap), "recovering leaves the swap for the save to take");
  }

  /**
   * Being launched on a path that is not there is vim's new file: an empty document whose name is that path, with no
   * error said and nothing unsaved about it. The other door is the opposite question and stays an error —
   * {@code :open /no/such/file} is a typo, while {@code visur /no/such/file} is a thing you are about to write — and
   * that half is asserted in {@link SessionsOnOneDocumentTest}.
   */
  @Test
  void launchingOnAPathThatDoesNotExistOpensANewFileAtThatPath() throws IOException {
    Path file = dir.resolve("notes.txt");

    bootServer();

    try (WireClient client = new WireClient(vertx, port, 60, 8, file.toString())) {
      assertEquals("", client.message().strip(), "a path you were asked to open and which is not there is not a failure: "
        + client.message());
      assertFalse(client.isDirty(), "an empty document at a path has nothing the file lacks");

      client.presses("i");
      client.types("first words");
      client.presses("Escape");
      client.presses("ZZ");

      assertFalse(client.isDirty(), "and the save of a document whose file was missing went where the name said");
      // The bytes are whatever a buffer that never had a final separator saves as; where they landed is what this test
      // is about, and whether that last newline is owed to the file is a question no ticket has asked yet.
      assertEquals("first words", Files.readString(file).strip());
    }
  }

  /**
   * A directory is not a document, and the answer is a sentence on the window that arrived rather than a stack trace
   * in a log nobody reads: the launch fails, the server comes up anyway, and the keyboard works. Before the launch
   * load went through {@code :open}'s door this threw out of {@code InitializerService}, which killed the verticle and
   * left the launcher saying "the server did not come up" about a typo in a path.
   */
  @Test
  void launchingOnADirectorySaysSoOnTheWindow() throws IOException {
    Path somewhereElse = dir.resolve("elsewhere");
    Files.createDirectories(somewhereElse);
    bootServer();

    try (WireClient client = new WireClient(vertx, port, 60, 8, somewhereElse.toString())) {
      assertTrue(client.message().contains("not a regular file"), "the launch failed out loud: " + client.message());
      assertFalse(client.isDirty(), "and nothing was loaded, so nothing is unsaved");

      client.presses("i");
      client.types("still usable");
      client.presses("Escape");

      assertTrue(client.text().contains("still usable"), "a failed launch is not a broken editor: " + client.text());
    }
  }

  /**
   * A file that will not open is a sentence on the window, not a stack trace in a log: the same door answers for a
   * directory and for a permission refusal, so this is the second half of {@link #launchingOnADirectorySaysSoOnTheWindow}
   * and it was green when written, because {@code LoadFileOp.open} already hands an {@code IOException} to the window
   * that asked.
   */
  @Test
  void launchingOnAFileThatCannotBeReadSaysSoOnTheWindow() throws IOException {
    Path unreadable = dir.resolve("secret.txt");
    Files.writeString(unreadable, "words you may not have\n");
    assumeTrue(unreadable.toFile().setReadable(false) && !Files.isReadable(unreadable),
      "this machine lets the test read it anyway");
    bootServer();

    try (WireClient client = new WireClient(vertx, port, 60, 8, unreadable.toString())) {
      // One line, naming the file, on the window that asked — which is the whole of it. The stack-trace version of this
      // left the server up and the screen blank with no explanation anywhere.
      assertTrue(client.message().startsWith("loadFile:"), "a sentence, not a trace: " + client.message());
      assertTrue(client.message().contains("secret.txt"), "naming the file it could not read: " + client.message());
      assertFalse(client.isDirty(), "and nothing was loaded, so there is no unsaved work");
    }
  }

  /**
   * A window arriving later is somebody joining the document, not a second launch: it is asked nothing, and what the
   * first window typed is still there when it arrives.
   *
   * <p>That used to be a latch on the process, and it fails strangely when the ask is read rather than taken: the window
   * that joined gets the swap question — the first window's typing put a swap beside the file, so a second launch finds
   * a conflict to ask about. A question about a launch you did not perform, in a document you did not open, is not a
   * question an answer to is available for.
   */
  @Test
  void aWindowArrivingLaterDoesNotReopenTheFileTheFirstWindowCameFor() throws IOException {
    Path file = dir.resolve("shared.txt");
    Files.writeString(file, "the file's own words\n");
    bootServer();

    WireClient first = new WireClient(vertx, port, 40, 10, file.toString());
    assertTrue(first.text().contains("the file's own words"), "the launch opened the file for the first window: ["
      + first.text().trim() + "]");
    first.presses("i");
    first.types("work in progress");
    first.presses("Escape");
    assertTrue(first.isDirty(), "the first window typed, and it is the file's text that has to survive that");

    try (WireClient second = new WireClient(vertx, port, 40, 10)) {
      assertEquals("", second.message().strip(), "a window joining is asked nothing about a launch it did not take");
      assertTrue(first.text().contains("work in progress"), "a window joining did not reload the file over the work");
      assertTrue(second.text().contains("work in progress"), "and it arrived at the work rather than at the file");
      assertTrue(second.isDirty(), "the work is the document's, so the window that only joined is warned too");
    }
  }

  /**
   * Two windows, two files: the second one does not get its file, because there is one document per process and the
   * first window is standing in it. The ask is answered where the asker can read it — its own window — and the answer
   * names the key that works inside a session.
   *
   * <p>This is the case {@code visur /path} used to refuse in the shell, before it had any way to know that the server
   * it found was idle. Refusing here is not a loss: the message is the advice, and it is said after the thing worth
   * protecting (a live editing session) is actually known.
   */
  @Test
  void aWindowArrivingForAnotherFileWhileAWindowIsEditingIsToldWhy() throws IOException {
    Path firstFile = dir.resolve("first.txt");
    Path secondFile = dir.resolve("second.txt");
    Files.writeString(firstFile, "the first document\n");

    bootServer();

    WireClient first = new WireClient(vertx, port, 40, 10, firstFile.toString());
    assertTrue(first.text().contains("the first document"), "the first window got its file: " + first.text());

    try (WireClient second = new WireClient(vertx, port, 40, 10, secondFile.toString())) {
      assertTrue(second.message().contains("another window is editing"), "the refusal says why: " + second.message());
      assertTrue(second.message().contains("second.txt"), "and names the file it did not open: " + second.message());
      assertTrue(second.text().contains("the first document"), "the document nobody asked to see is still the document");
    }
    assertFalse(first.text().contains("second.txt"), "the first window was not spoken to about the stranger's file");
    assertFalse(secondFile.toFile().exists(), "and refusing a missing file does not leave a file behind");
  }

  /**
   * A server started with no file has nothing to recover and nobody's work to mirror, so it leaves no pool slot
   * behind either. Without this, every browser session ever started would claim a letter before the first keystroke.
   */
  @Test
  void aBootWithNothingTypedClaimsNoSlot() throws IOException {
    Path pool = dir.resolve("pool");
    Files.createDirectories(pool);
    System.setProperty("visur.swapDir", pool.toString());

    bootServer();

    try (WireClient client = new WireClient(vertx, port, 60, 8)) {
      assertEquals("", client.text().strip(), "no startup file, no content");
    }
    try (var slots = Files.newDirectoryStream(pool, ".sw*")) {
      for (Path slot : slots) {
        fail("an empty document claimed a pool slot: " + slot);
      }
    }
  }
}