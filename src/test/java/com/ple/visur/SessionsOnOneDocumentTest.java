package com.ple.visur;

import io.vertx.rxjava3.core.Vertx;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Stack;

import static com.ple.visur.EditorSubmode.navigate;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Two pairs of hands, one document.
 *
 * <p>Ticket 03's split is between the text and the hands holding it, and the only test that can tell a split from a
 * renaming is one with two of the latter looking at one of the former. Every assertion here says the same thing twice:
 * what one window did, and what the other window still shows.
 */
class SessionsOnOneDocumentTest {

  private static final String CONTENT = "aaaabbbbccccddddeeeeffff\n";
  private static final int PORT = 8950;

  private static int nextPort;

  private Vertx vertx;
  private int port;
  private EditorModelCoupler emc;

  @BeforeEach
  void startTheServer() {
    // The port is a system property rather than an environment variable so a test can set it without spawning a
    // process, and it is a port a developer's `visur` is not sitting on. Per test rather than per class: a Vert.x
    // close that has completed does not always hand the listening socket back in time for the next test in this JVM
    // to bind it, and a server that could not bind used to start anyway and answer nothing.
    // See MainVerticle.couldNotListen.
    port = PORT + nextPort++;
    System.setProperty("visur.port", String.valueOf(port));
    vertx = Vertx.vertx();
    vertx.deployVerticle(new MainVerticle()).blockingGet();
    ServiceHolder.initializerService.initializeEditorModel();
    emc = ServiceHolder.editorModelCoupler;
    emc.setSourceFilePath("");
    emc.putEditorContent(CONTENT);
    emc.session().putCanvasStart(0);
    emc.putCA(0);
    Stack<EditorSubmode> submodes = new Stack<>();
    submodes.push(navigate);
    emc.putEditorSubmodeStack(submodes);
    emc.putIsInCommandState(false);
    ViewportPerClientTest.awaitNoConnections();
  }

  @AfterEach
  void stopTheServer() {
    // Blocking, so the server is gone before the next test asks for the port range back.
    vertx.close().blockingAwait();
  }

  @Test
  void aScrolledWindowDoesNotScrollItsNeighbour() {
    // Ten columns by two rows for twenty-four characters: three wrapped rows will not fit, so a window that follows
    // its own cursor off the bottom has to scroll sideways along the line to keep it in sight.
    try (WireClient first = new WireClient(vertx, port, 10, 2);
         WireClient second = new WireClient(vertx, port, 10, 2)) {
      assertEquals(0, second.origin(), "both windows start at the beginning of the document");

      for (int step = 0; step < 22; step++) {
        first.presses("l");
      }

      assertTrue(first.origin() > 0, "the window that followed its cursor scrolled along the line");
      assertEquals(0, second.origin(), "the window that stayed home did not go with it");
    }
  }

  @Test
  void aCursorBelongsToTheWindowThatMovedIt() {
    try (WireClient first = new WireClient(vertx, port, 40, 10);
         WireClient second = new WireClient(vertx, port, 40, 10)) {
      assertEquals(0, second.cursorQuantumStart(), "both windows start at the beginning");

      first.presses("l");
      first.presses("l");
      first.presses("l");
      assertEquals(3, first.cursorQuantumStart(), "the window that moved right three times is three characters in");
      assertEquals(0, second.cursorQuantumStart(), "the other window has not moved, and will not until its own keys arrive");
    }
  }

  @Test
  void aSelectionBelongsToTheWindowThatMadeIt() {
    try (WireClient first = new WireClient(vertx, port, 40, 10);
         WireClient second = new WireClient(vertx, port, 40, 10)) {
      // A window that joins selects the quantum its cursor is on, exactly as the first window does, so the
      // interesting baseline is whatever the neighbour was already showing rather than a number from the spec.
      int untouched = second.span();

      first.presses("5");
      first.presses("s");
      assertEquals(5, first.span(), "the window that asked for five quantums has five selected");
      assertEquals(untouched, second.span(), "the other window is still showing what it showed before its neighbour selected");
    }
  }

  @Test
  void aTypedCountBelongsToTheWindowThatTypedIt() {
    try (WireClient first = new WireClient(vertx, port, 40, 10);
         WireClient second = new WireClient(vertx, port, 40, 10)) {
      first.presses("5");
      assertEquals(5, first.pendingCount(), "the window that typed 5 is counting 5");

      second.presses("3");
      assertEquals(3, second.pendingCount(), "the window that typed 3 is counting 3");
      assertEquals(5, first.pendingCount(), "and the first window is still counting what its own hands typed");
    }
  }
}
