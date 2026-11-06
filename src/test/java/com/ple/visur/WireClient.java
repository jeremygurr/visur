package com.ple.visur;

import io.vertx.core.json.JsonObject;
import io.vertx.rxjava3.core.Vertx;
import io.vertx.rxjava3.core.http.WebSocket;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * A client on the wire: a socket, a window size, and a queue of what the server has told it to paint.
 *
 * <p>Everything worth knowing about per-client state is invisible to a test that reaches into the model, because the
 * model has no way to admit it is holding two clients' anything. So this is the seam: connect, declare a grid, press
 * keys, read the view that comes back. That is what a client knows.
 */
final class WireClient implements AutoCloseable {

  private final WebSocket socket;
  private final LinkedBlockingQueue<String> frames = new LinkedBlockingQueue<>();
  private final int port;
  private int cols;
  private int rows;

  WireClient(Vertx vertx, int port, int cols, int rows) {
    this(vertx, port);
    this.cols = cols;
    this.rows = rows;
    declaresItsViewport();
  }

  /** A client that connects and says nothing about its window, which is what a headless boot and a pty look like. */
  WireClient(Vertx vertx, int port) {
    this.port = port;
    socket = vertx.createHttpClient().webSocket(port, "localhost", "/ws").blockingGet();
    socket.textMessageHandler(frames::add);
  }

  void send(String address, JsonObject body) {
    socket.writeTextMessage(new JsonObject().put("address", address).put("body", body).encode());
  }

  void presses(String key) {
    send(BusEvent.keyWasPressed.name(), new JsonObject().put("key", key));
  }

  /** This client's window is now this big, which is the only thing it ever sends unprompted. */
  void resizesTo(int cols, int rows) {
    this.cols = cols;
    this.rows = rows;
    declaresItsViewport();
  }

  private void declaresItsViewport() {
    send(BusEvent.viewportWasChanged.name(), new JsonObject().put("cols", cols).put("rows", rows));
  }

  /**
   * What this client is being told to paint right now.
   *
   * <p>Every change repaints every client, so a socket's queue holds views of states that have already moved on.
   * Emptying the queue and then re-declaring this client's own grid is the cheapest way to be sure the frame read
   * next describes the model as it is now: a viewport declaration repaints, and changes nothing else.
   */
  JsonObject view() {
    while (poll(250) != null) {
      // still catching up on frames an earlier trigger set in motion
    }
    declaresItsViewport();
    return nextView();
  }

  /** How wide this client was told to wrap at, read off the first row it was served. */
  int rowWidth() {
    return rowWidthOf(view());
  }

  static int rowWidthOf(JsonObject view) {
    var rows = view.getJsonArray("rows");
    assertNotNull(rows, "the view carried no rows at all: " + view.encode());
    return rows.getJsonObject(0).getString("text").length();
  }

  int pendingCount() {
    return view().getInteger("pendingCount");
  }

  int span() {
    return view().getInteger("span");
  }

  int origin() {
    return view().getInteger("origin");
  }

  int cursorQuantumStart() {
    return view().getInteger("cursorQuantumStart");
  }

  int cursorCol() {
    return view().getInteger("cursorCol");
  }

  String editorMode() {
    return view().getString("editorMode");
  }

  String submode() {
    return view().getString("editorSubmode");
  }

  String message() {
    return view().getString("message");
  }

  /** The next view this socket was sent, without asking for one: for a client that must not declare a grid. */
  JsonObject nextView() {
    for (int patience = 0; patience < 200; patience++) {
      String frame = poll(100);
      if (frame == null) {
        continue;
      }
      JsonObject envelope = new JsonObject(frame);
      if (BusEvent.viewWasChanged.name().equals(envelope.getString("address"))) {
        Object body = envelope.getValue("body");
        return body instanceof JsonObject asObject ? asObject : new JsonObject((String) body);
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
