package com.ple.visur;

import io.vertx.rxjava3.core.Vertx;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

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
 * <p>{@code visur.file} is the startup file as a system property, for the same reason {@code visur.port} is: a test
 * cannot set an environment variable for the process it is already running in, and the alternative was a test that
 * spawns a JVM.
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
    System.setProperty("visur.file", file.toString());

    bootServer();

    try (WireClient client = new WireClient(vertx, port, 60, 8)) {
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
    System.setProperty("visur.file", file.toString());

    bootServer();

    try (WireClient client = new WireClient(vertx, port, 60, 8)) {
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
    System.setProperty("visur.file", file.toString());

    bootServer();

    try (WireClient client = new WireClient(vertx, port, 60, 8)) {
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
    System.setProperty("visur.file", somewhereElse.toString());

    bootServer();

    try (WireClient client = new WireClient(vertx, port, 60, 8)) {
      assertTrue(client.message().contains("not a regular file"), "the launch failed out loud: " + client.message());
      assertFalse(client.isDirty(), "and nothing was loaded, so nothing is unsaved");

      client.presses("i");
      client.types("still usable");
      client.presses("Escape");

      assertTrue(client.text().contains("still usable"), "a failed launch is not a broken editor: " + client.text());
    }
  }

  /**
   * The launch file is opened once, by the first window, and never again: a window arriving later is somebody joining
   * the document, not a second launch, and running the open again would drop what the first window typed.
   *
   * <p>The joiner is not asked about the launch either. That is the half that fails first when the request is read
   * instead of taken, and it fails strangely: the window that joined gets the swap question — the first window's typing
   * put a swap beside the file, so a second launch finds a conflict to ask about. A question about a launch you did
   * not perform, in a document you did not open, is not a question an answer to is available for.
   */
  @Test
  void aWindowArrivingLaterDoesNotReopenTheFileTheProcessWasLaunchedOn() throws IOException {
    Path file = dir.resolve("shared.txt");
    Files.writeString(file, "the file's own words\n");
    System.setProperty("visur.file", file.toString());

    bootServer();

    WireClient first = new WireClient(vertx, port, 40, 10);
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
