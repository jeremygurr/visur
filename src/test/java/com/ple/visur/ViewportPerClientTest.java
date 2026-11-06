package com.ple.visur;

import io.vertx.rxjava3.core.Vertx;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Stack;

import static com.ple.visur.EditorSubmode.navigate;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Two clients, two window sizes, one document.
 *
 * <p>{@code canvasWidth} and {@code canvasHeight} used to be single fields in the shared model, written by whichever
 * client resized last, so resizing a browser window rewrapped the terminal that was looking at the same document. The
 * bug is only visible with two real connections, which is why every test here is a socket: the model has no way to
 * admit it is holding two clients' geometry, and a unit test of the model could never catch the thing.
 *
 * <p>The seam is the wire — see {@link WireClient}.
 */
class ViewportPerClientTest {

  /** Twenty-four characters wrap at ten and twenty but not at eighty. */
  private static final String CONTENT = "aaaabbbbccccddddeeeeffff\n";
  private static final int PORT = 8900;

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
    // After the deploy, because MainVerticle.start installs the services and initialises the model: content put
    // before it is a document the server never sees.
    ServiceHolder.initializerService.initializeEditorModel();
    emc = ServiceHolder.editorModelCoupler;
    emc.setSourceFilePath("");
    emc.putEditorContent(CONTENT);
    emc.putCanvasStart(0);
    emc.putCA(0);
    Stack<EditorSubmode> submodes = new Stack<>();
    submodes.push(navigate);
    emc.putEditorSubmodeStack(submodes);
    emc.putIsInCommandState(false);
    // Connections is static and the test JVM is shared between classes, so a socket another class forgot, or had not
    // finished forgetting, would otherwise be counted here.
    awaitNoConnections();
  }

  @AfterEach
  void stopTheServer() {
    // Blocking, so the server is gone before the next test asks for the port range back.
    vertx.close().blockingAwait();
  }

  @Test
  void twoClientsDeclareDifferentWidthsAndEachSeesItsOwn() {
    try (WireClient wide = new WireClient(vertx, port, 20, 10);
         WireClient narrow = new WireClient(vertx, port, 10, 10)) {
      assertEquals(20, wide.rowWidth(), "the wide client sees its own width");
      assertEquals(10, narrow.rowWidth(), "the narrow client sees its own width");
    }
  }

  @Test
  void aResizeOnOneClientLeavesTheOtherLookingByteIdentical() {
    try (WireClient wide = new WireClient(vertx, port, 20, 10);
         WireClient narrow = new WireClient(vertx, port, 10, 10)) {
      String before = wide.view().encode();

      narrow.resizesTo(7, 10);
      assertEquals(7, narrow.rowWidth(), "the client that resized got its new grid");

      assertEquals(before, wide.view().encode(), "row for row, cursor for cursor, the other client saw nothing happen");
    }
  }

  @Test
  void aThirdClientJoiningChangesNothingAboutWhatTheFirstTwoAreShown() {
    try (WireClient wide = new WireClient(vertx, port, 20, 10);
         WireClient narrow = new WireClient(vertx, port, 10, 10)) {
      String wideBefore = wide.view().encode();
      String narrowBefore = narrow.view().encode();

      // Fifteen, not forty: the document is twenty-four characters, and any width past that looks identical, which
      // is a poor way to ask whether the newcomer is being drawn at its own width.
      try (WireClient middle = new WireClient(vertx, port, 15, 10)) {
        assertEquals(15, middle.rowWidth(), "the newcomer gets its own grid");
      }

      assertEquals(wideBefore, wide.view().encode(), "the wide client was not disturbed by anyone arriving");
      assertEquals(narrowBefore, narrow.view().encode(), "nor was the narrow one");
    }
  }

  @Test
  void aDisconnectTakesTheViewportWithIt() {
    WireClient leaving = new WireClient(vertx, port, 12, 10);
    try (WireClient staying = new WireClient(vertx, port, 20, 10)) {
      assertEquals(2, Connections.count(), "two sockets, two viewports");

      leaving.close();

      assertEquals(1, countedUntilItSettles(), "the server stopped remembering the socket that left; ids=" + Connections.ids());
      assertEquals(20, staying.rowWidth(), "and the client that stayed is none the worse for it");
    }
  }

  /** A close is reported to the server asynchronously, so this waits for the bookkeeping rather than sleeping at it. */
  private int countedUntilItSettles() {
    for (int patience = 0; patience < 400; patience++) {
      if (Connections.count() <= 1) {
        break;
      }
      pause();
    }
    return Connections.count();
  }

  static void awaitNoConnections() {
    for (int patience = 0; patience < 40 && Connections.count() > 0; patience++) {
      pause();
    }
  }

  static void pause() {
    try {
      Thread.sleep(50);
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
    }
  }

  @Test
  void aClientThatNeverDeclaresAGridIsStillGivenOne() {
    // The boot case, and the reason a viewport has a default at all: a server that answers a keypress by throwing
    // because nobody has resized a window yet is a server nobody can start headless.
    WireClient mute = new WireClient(vertx, port);

    mute.presses("w");

    assertEquals(24, WireClient.rowWidthOf(mute.nextView()),
      "the default eighty columns leave a twenty-four character line unwrapped");
    mute.close();
  }
}
