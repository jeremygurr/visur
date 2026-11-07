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
   * The rule that replaces the old recovery: a swap is not boot's business. It used to load the swap into the buffer,
   * write a second copy beside the file, delete the swap and say nothing — so the buffer held text nobody chose, next
   * to a file nobody had heard of.
   *
   * <p>The swap staying is not laziness. It is the only copy of work that has not been recovered yet, and a boot that
   * consumes it has lost it if the user then quits without saving.
   */
  @Test
  void aSwapBesideTheFileIsNeitherLoadedNorDeletedAtBoot() throws IOException {
    Path file = dir.resolve("crashed.txt");
    Files.writeString(file, "what the file has\n");
    Path swap = dir.resolve(".crashed.txt.swp");
    Files.writeString(swap, "what only the swap has\n");
    System.setProperty("visur.file", file.toString());

    bootServer();

    try (WireClient client = new WireClient(vertx, port, 60, 8)) {
      String painted = client.text();
      assertTrue(painted.contains("what the file has"), "boot loads the file it was given: " + painted);
      assertFalse(painted.contains("what only the swap has"), "the swap is offered at open time, not assumed at boot");
    }
    assertEquals("what only the swap has\n", Files.readString(swap),
      "a swap that has not been answered is still somebody's work");
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
