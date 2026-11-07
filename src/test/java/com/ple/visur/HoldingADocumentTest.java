package com.ple.visur;

import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;
import io.vertx.rxjava3.core.Vertx;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A server nobody is looking at holds no document.
 *
 * <p>Visur's server outlives its clients on purpose — that is what {@code ZQ} means — and the first version of that
 * kept the document in memory while it waited. So the machine held somebody's unsaved file indefinitely, and a second
 * {@code visur other.txt} attached to a server already busy with a different one. Now the last window to go takes the
 * text with it: work goes to the file it belongs to, and what cannot go to a file leaves its swap behind.
 *
 * <p>The distinction doing the work is <em>left</em> versus <em>dropped</em>. A client that says goodbye has decided
 * about its work; a connection that fell over is a client that never got the chance, and a browser reloading its tab
 * is not a user who has finished. So the release waits for a client that said so, and a document whose window vanished
 * stays exactly where it was.
 *
 * <p>Every test here goes over a real socket, because "the last window" is a fact about sockets and nothing else.
 */
class HoldingADocumentTest {

  private static final String CONTENT = "one two\n";
  private static final int PORT = 9050;

  private static int nextPort;

  @TempDir
  Path dir;

  private Vertx vertx;
  private int port;
  private EditorModelCoupler emc;

  /** A server on a port of its own, with a swap pool in the test's directory rather than in the tree. */
  private void bootServer() {
    port = PORT + nextPort++;
    System.setProperty("visur.port", String.valueOf(port));
    System.setProperty("visur.swapDir", dir.resolve("swaps").toString());
    vertx = Vertx.vertx();
    vertx.deployVerticle(new MainVerticle()).blockingGet();
    // The model is a process, and the JVM outlives a test class, so every test starts from the same clean document
    // rather than from whatever the last one left in it.
    emc = ServiceHolder.editorModelCoupler;
    emc.document().setSourceFilePath("");
    emc.document().putContentAsSaved(CONTENT);
  }

  @AfterEach
  void stopTheServer() {
    System.clearProperty("visur.port");
    System.clearProperty("visur.swapDir");
    if (vertx != null) {
      vertx.close().blockingAwait();
    }
  }

  private Path file(String name, String text) throws IOException {
    Path file = dir.resolve(name);
    Files.writeString(file, text);
    return file;
  }

  /** One character of unsaved work, typed by this window at the front of the text. */
  private void typeACharacter(WireClient window) {
    window.presses("i");
    window.types("x");
    window.presses("Escape");
    // The view is the sync point. A test that closes a socket straight after typing would be closing it before the
    // server had read the keys, and would then be asserting about a document that never had the character in it.
    assertTrue(window.isDirty(), "the typing reached the server");
  }

  private String get(String path) {
    return vertx.createHttpClient().request(HttpMethod.GET, port, "localhost", path).blockingGet()
      .send().blockingGet().body().blockingGet().toString();
  }

  /**
   * How the launcher knows before it attaches. It cannot ask a running server what it is holding by any other means,
   * and "somebody is editing this document" is the one reason it has to refuse a file rather than open it.
   */
  @Test
  void theServerSaysWhetherAWindowIsEditing() throws IOException {
    Path file = file("work.txt", CONTENT);
    bootServer();
    assertEquals(0, new JsonObject(get("/status")).getInteger("windows"), "a server with no windows");

    try (WireClient window = new WireClient(vertx, port, 60, 8, file.toString())) {
      JsonObject status = new JsonObject(get("/status"));
      assertEquals(1, status.getInteger("windows"), "one window is attached");
      assertEquals(file.toString(), status.getString("document"), "and the answer names what is in the way");
    }
  }

  /** The server notices a socket went on its own event loop, so "after the close" is a wait and not a statement. */
  private void awaitTrue(BooleanSupplier condition, String what) throws InterruptedException {
    for (int attempt = 0; attempt < 60; attempt++) {
      if (condition.getAsBoolean()) {
        return;
      }
      Thread.sleep(50);
    }
    fail("timed out waiting for " + what);
  }

  /**
   * The rule the user asked for, in its ordinary shape: {@code ZQ} leaves the server running, and what the file does
   * not have by then, the file has when the window goes.
   *
   * <p>The save happens on leaving rather than on {@code :w} being pressed, which is a decision and not an accident:
   * the alternative is a server that drops a document it was never allowed to write, and dropping it quietly is the one
   * outcome worse than a save nobody typed.
   */
  @Test
  void theLastWindowToSayGoodbyeTakesItsUnsavedWorkToItsFile() throws Exception {
    Path file = file("work.txt", CONTENT);
    bootServer();

    WireClient window = new WireClient(vertx, port, 60, 8, file.toString());
    typeACharacter(window);

    window.send(BusEvent.windowWasClosed.name(), new io.vertx.core.json.JsonObject());
    window.close();

    awaitTrue(() -> emc.document().getContent().isEmpty(), "the server to let go of the document");
    assertEquals("x" + CONTENT, Files.readString(file), "the file has what the server was holding");
    assertEquals("", emc.document().getSourceFilePath(), "and there is no document left holding that name");
    assertFalse(emc.document().isDirty(), "an empty document with no file has nothing unsaved");
  }

  /** The next window to arrive finds an empty editor, not the file somebody else was last looking at. */
  @Test
  void theWindowAfterTheOneThatLeftStartsFromNothing() throws Exception {
    Path file = file("work.txt", CONTENT);
    bootServer();

    WireClient first = new WireClient(vertx, port, 60, 8, file.toString());
    typeACharacter(first);
    first.send(BusEvent.windowWasClosed.name(), new io.vertx.core.json.JsonObject());
    first.close();
    awaitTrue(() -> emc.document().getContent().isEmpty(), "the server to let go of the document");

    try (WireClient second = new WireClient(vertx, port, 60, 8)) {
      assertEquals("", second.text(), "nothing is on screen, because nothing is held");
    }
  }

  /**
   * A socket that died is not a client that has finished. A dropped connection, a killed terminal and a browser
   * reloading its tab all look like this, and none of them is a person who decided what should happen to their work.
   */
  @Test
  void aWindowThatDroppedRatherThanLeftKeepsTheDocument() throws Exception {
    Path file = file("work.txt", CONTENT);
    bootServer();

    WireClient window = new WireClient(vertx, port, 60, 8, file.toString());
    typeACharacter(window);
    window.close(); // no goodbye, exactly as a dropped connection arrives

    awaitTrue(() -> Connections.count() == 0, "the server to notice the socket went");
    assertEquals("x" + CONTENT, emc.document().getContent(), "the work is still where it was");
    assertEquals(CONTENT, Files.readString(file), "and nothing was written to answer a request nobody made");
  }

  /**
   * {@code :q!} says throw it away, and the release must not turn around and save it: the discard happens as the
   * window goes, so by the time the server is empty there is nothing left to write.
   */
  @Test
  void theLastWindowThrowingItsWorkAwayWritesNothing() throws Exception {
    Path file = file("work.txt", CONTENT);
    bootServer();

    WireClient window = new WireClient(vertx, port, 60, 8, file.toString());
    typeACharacter(window);

    window.presses(":");
    window.types("q!");
    window.presses("Enter");
    assertTrue(window.waitsToBeClosedByTheServer(), "the server let the window go, as :q! means");

    awaitTrue(() -> emc.document().getContent().isEmpty(), "the discarded document to be gone");
    assertEquals(CONTENT, Files.readString(file), "throwing a buffer away says nothing about the file");
  }

  /**
   * Work with no file to go to is disposed of, and the swap is what makes that safe rather than a loss: it was written
   * while the text was typed, and it outlives the buffer, which is what the next window on that name will be asked
   * about.
   */
  @Test
  void aNamelessBuffersSwapOutlivesTheBufferThatSaidGoodbye() throws Exception {
    bootServer();

    WireClient window = new WireClient(vertx, port, 60, 8, ClientKind.TERMINAL);
    // The swap is throttled rather than queued, and the clock it throttles against outlives a test class, so a
    // keystroke typed just after another test wrote a swap writes nothing at all.
    Thread.sleep(600);
    typeACharacter(window);

    window.send(BusEvent.windowWasClosed.name(), new io.vertx.core.json.JsonObject());
    window.close();

    awaitTrue(() -> emc.document().getContent().isEmpty(), "the buffer to be gone");
    try (java.util.stream.Stream<Path> pool = Files.list(dir.resolve("swaps"))) {
      List<String> written = pool.map(HoldingADocumentTest::read).toList();
      assertTrue(written.contains("x" + CONTENT), "the text is still on disk, in a swap: " + written);
    }
  }

  private static String read(Path path) {
    try {
      return Files.readString(path);
    } catch (IOException unreadable) {
      throw new IllegalStateException(unreadable);
    }
  }

  /** The release belongs to the last window, so a document another window is still editing is nobody's to save. */
  @Test
  void oneWindowLeavingWhileAnotherStaysLeavesTheDocumentAlone() throws Exception {
    Path file = file("work.txt", CONTENT);
    bootServer();

    WireClient first = new WireClient(vertx, port, 60, 8, file.toString());
    WireClient second = new WireClient(vertx, port, 60, 8);
    typeACharacter(first);

    first.send(BusEvent.windowWasClosed.name(), new io.vertx.core.json.JsonObject());
    first.close();
    awaitTrue(() -> Connections.count() == 1, "the server to notice one window went");

    assertEquals("x" + CONTENT, emc.document().getContent(), "the window still looking at it has not lost anything");
    assertEquals(CONTENT, Files.readString(file), "and the other window's goodbye is not a licence to write");
  }
}
