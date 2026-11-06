package com.ple.visur;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.rxjava3.core.Vertx;
import io.vertx.rxjava3.core.http.WebSocket;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static com.ple.visur.EditorSubmode.navigate;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Two clients, two window sizes, one document.
 *
 * <p>{@code canvasWidth} and {@code canvasHeight} used to be single fields in the shared model, written by whichever
 * client resized last, so resizing a browser window rewrapped the terminal that was looking at the same document. The
 * bug is only visible with two real connections, which is why every test here is a socket: the model has no way to
 * admit it is holding two clients' geometry, and a unit test of the model could never catch the thing.
 *
 * <p>The seam is the wire: connect, declare a grid, read the view that comes back. That is what a client knows.
 */
class ViewportPerClientTest {

  /** Twenty-four characters wrap at ten and twenty but not at eighty. */
  private static final String CONTENT = "aaaabbbbccccddddeeeeffff\n";
  private static final int PORT = 8899;

  private Vertx vertx;
  private EditorModelCoupler emc;

  @BeforeAll
  static void askForATestPort() {
    // The port is a system property rather than an environment variable so a test can bind it without spawning a
    // process, and it is not the port a developer's `visur` is sitting on.
    System.setProperty("visur.port", String.valueOf(PORT));
  }

  @BeforeEach
  void startTheServer() {
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
    java.util.Stack<EditorSubmode> submodes = new java.util.Stack<>();
    submodes.push(navigate);
    emc.putEditorSubmodeStack(submodes);
    emc.putIsInCommandState(false);
  }

  @AfterEach
  void stopTheServer() {
    vertx.close();
  }

  /** One client's socket: it speaks the bus envelope, exactly as the terminal client and the browser do. */
  private final class Client implements AutoCloseable {
    private final LinkedBlockingQueue<String> frames = new LinkedBlockingQueue<>();
    private final WebSocket socket;
    private int cols;
    private int rows;

    Client(int cols, int rows) {
      socket = vertx.createHttpClient().webSocket(PORT, "localhost", "/ws").blockingGet();
      socket.textMessageHandler(frames::add);
      this.cols = cols;
      this.rows = rows;
      declaresItsViewport();
    }

    void send(String address, JsonObject body) {
      socket.writeTextMessage(new JsonObject().put("address", address).put("body", body).encode());
    }

    void resizesTo(int cols, int rows) {
      this.cols = cols;
      this.rows = rows;
      declaresItsViewport();
    }

    private void declaresItsViewport() {
      send(BusEvent.viewportWasChanged.name(), new JsonObject().put("cols", cols).put("rows", rows));
    }

    void presses(String key) {
      send(BusEvent.keyWasPressed.name(), new JsonObject().put("key", key));
    }

    /**
     * What this client is being told to paint right now.
     *
     * <p>Every change repaints every client, so a socket's queue holds views of states that have already moved on.
     * Emptying the queue and then re-declaring this client's own grid is the cheapest way to be sure the frame read
     * next describes the model as it is now: a viewport declaration repaints, and changes nothing else.
     */
    JsonObject view() {
      // Drain what is already in flight first. Every change repaints every client, so a view rendered for this socket
      // by another client's earlier trigger can still be on the wire when this client asks to read. Emptying the queue
      // after it has gone quiet, and then triggering a repaint of our own, is what makes the frame read next describe
      // the model as it is now. Local delivery takes microseconds and the quiet window a quarter of a second, so this
      // is the only waiting in the class -- and it waits for silence, not for a guess at how long things take.
      while (poll(250) != null) {
        // still catching up on frames an earlier trigger set in motion
      }
      declaresItsViewport();
      return nextView();
    }

    /** The next view this socket was sent, or a failure that says how long it waited. */
    private JsonObject nextView() {
      for (int patience = 0; patience < 200; patience++) {
        String frame = poll(100);
        if (frame == null) {
          continue;
        }
        JsonObject envelope = new JsonObject(frame);
        if (BusEvent.viewWasChanged.name().equals(envelope.getString("address"))) {
          return envelope.getValue("body") instanceof JsonObject body ? body : new JsonObject((String) envelope.getValue("body"));
        }
      }
      return fail("no view arrived on this socket after it declared a " + cols + " by " + rows + " grid");
    }

    private String poll(long patienceInMillis) {
      try {
        return frames.poll(patienceInMillis, TimeUnit.MILLISECONDS);
      } catch (InterruptedException interrupted) {
        Thread.currentThread().interrupt();
        return null;
      }
    }

    @Override
    public void close() {
      socket.close();
    }
  }

  /** How wide the client was told to wrap at, read off the first row it was served. */
  private static int rowWidth(JsonObject view) {
    JsonArray rows = view.getJsonArray("rows");
    assertNotNull(rows, "the view carried no rows at all: " + view.encode());
    return rows.getJsonObject(0).getString("text").length();
  }

  @Test
  void twoClientsDeclareDifferentWidthsAndEachSeesItsOwn() {
    try (Client wide = new Client(20, 10); Client narrow = new Client(10, 10)) {
      assertEquals(20, rowWidth(wide.view()), "the wide client sees its own width");
      assertEquals(10, rowWidth(narrow.view()), "the narrow client sees its own width");
    }
  }

  @Test
  void aResizeOnOneClientLeavesTheOtherLookingByteIdentical() {
    try (Client wide = new Client(20, 10); Client narrow = new Client(10, 10)) {
      String before = wide.view().encode();

      narrow.resizesTo(7, 10);
      assertEquals(7, rowWidth(narrow.view()), "the client that resized got its new grid");

      assertEquals(before, wide.view().encode(), "row for row, cursor for cursor, the other client saw nothing happen");
    }
  }

  @Test
  void aThirdClientJoiningChangesNothingAboutWhatTheFirstTwoAreShown() {
    try (Client wide = new Client(20, 10); Client narrow = new Client(10, 10)) {
      String wideBefore = wide.view().encode();
      String narrowBefore = narrow.view().encode();

      // Fifteen, not forty: the document is twenty-four characters, and any width past that looks identical, which
      // is a poor way to ask whether the newcomer is being drawn at its own width.
      try (Client middle = new Client(15, 10)) {
        assertEquals(15, rowWidth(middle.view()), "the newcomer gets its own grid");
      }

      assertEquals(wideBefore, wide.view().encode(), "the wide client was not disturbed by anyone arriving");
      assertEquals(narrowBefore, narrow.view().encode(), "nor was the narrow one");
    }
  }

  @Test
  void aDisconnectTakesTheViewportWithIt() {
    Client leaving = new Client(12, 10);
    try (Client staying = new Client(20, 10)) {
      assertEquals(2, Connections.count(), "two sockets, two viewports");

      leaving.close();

      assertEquals(1, countedUntilItSettles(), "the server stopped remembering the socket that left");
      assertEquals(20, rowWidth(staying.view()), "and the client that stayed is none the worse for it");
    }
  }

  /** A close is reported to the server asynchronously, so this waits for the bookkeeping rather than sleeping at it. */
  private int countedUntilItSettles() {
    for (int patience = 0; patience < 40; patience++) {
      if (Connections.count() <= 1) {
        break;
      }
      try {
        Thread.sleep(50);
      } catch (InterruptedException interrupted) {
        Thread.currentThread().interrupt();
        break;
      }
    }
    return Connections.count();
  }

  @Test
  void aClientThatNeverDeclaresAGridIsStillGivenOne() {
    // The boot case, and the reason a viewport has a default at all: a server that answers a keypress by throwing
    // because nobody has resized a window yet is a server nobody can start headless.
    WebSocket mute = vertx.createHttpClient().webSocket(PORT, "localhost", "/ws").blockingGet();
    LinkedBlockingQueue<String> frames = new LinkedBlockingQueue<>();
    mute.textMessageHandler(frames::add);

    mute.writeTextMessage(new JsonObject()
      .put("address", BusEvent.keyWasPressed.name())
      .put("body", new JsonObject().put("key", "w")).encode());

    JsonObject view = null;
    for (int patience = 0; patience < 40 && view == null; patience++) {
      String frame = poll(frames, 100);
      if (frame == null || !BusEvent.viewWasChanged.name().equals(new JsonObject(frame).getString("address"))) {
        continue;
      }
      Object body = new JsonObject(frame).getValue("body");
      view = body instanceof JsonObject asObject ? asObject : new JsonObject((String) body);
    }
    assertNotNull(view, "a keypress with no viewport declared got no view at all");
    assertEquals(24, rowWidth(view), "the default eighty columns leave a twenty-four character line unwrapped");
    mute.close();
  }

  private static String poll(LinkedBlockingQueue<String> frames, long millis) {
    try {
      return frames.poll(millis, TimeUnit.MILLISECONDS);
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      return null;
    }
  }
}
