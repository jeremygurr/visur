package com.ple.visur;

import io.vertx.rxjava3.core.eventbus.Message;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Who is attached to the server, and what grid each of them said it can paint.
 *
 * <p>The viewport used to be two fields in the shared editor model, so the second client to resize decided how the
 * first one's lines wrapped. Geometry is a property of a connection, not of a document, and this class is the only
 * place it is kept.
 *
 * <p>A connection is a socket, and its id is minted by the server when the socket is accepted (see
 * {@link MainVerticle#webSocket}). Clients never name themselves: a socket that could claim another's id could claim
 * its geometry too, and {@code viewportWasChanged} would be a way to move someone else's cursor.
 *
 * <p>Static, unlike the model, because it is transport state rather than editor state: {@link MainVerticle}'s socket
 * handlers write it and the verticles read it, and it is empty the moment everybody disconnects.
 */
public final class Connections {

  /**
   * The bus header carrying the connection a message is executing for, in both directions. Set by the socket that
   * relayed a frame, never by the client, and used on the way out so a socket forwards only the view rendered for
   * its own grid.
   */
  public static final String HEADER = "connection";

  /** The connection of a message that arrived over no socket at all, and of every test that never opened one. */
  public static final String NONE = "";

  /** What a server with nobody attached renders, so a headless start and the pty tests still have a grid to draw. */
  private static final Viewport DEFAULT = new Viewport(80, 24);

  /** One client's content grid, in cells. Rows are content rows: the client subtracts its own chrome. */
  public record Viewport(int cols, int rows) {
  }

  private static final Map<String, Viewport> byId = new ConcurrentHashMap<>();

  private Connections() {
  }

  /** The id on a message, or {@link #NONE} when it came from something that is not a connection we minted. */
  public static String headerOf(Message<?> message) {
    String id = message.headers().get(HEADER);
    return id == null ? NONE : id;
  }

  /** A socket was accepted. It gets the default grid until it declares its own. */
  public static void open(String id) {
    byId.put(id, DEFAULT);
  }

  /** A socket closed. Its grid goes with it, which is what keeps this map from growing by one per browser reload. */
  public static void close(String id) {
    byId.remove(id);
  }

  public static void resize(String id, int cols, int rows) {
    byId.put(id, new Viewport(cols, rows));
  }

  public static Viewport viewport(String id) {
    return byId.getOrDefault(id, DEFAULT);
  }

  /** Every open connection, including {@link #NONE}: a view has to be rendered for each of them. */
  public static Set<String> ids() {
    Set<String> ids = ConcurrentHashMap.newKeySet();
    ids.addAll(byId.keySet());
    ids.add(NONE);
    return ids;
  }

  /** How many sockets are being remembered. Zero means nothing has accumulated. */
  public static int count() {
    return (int) byId.keySet().stream().filter(id -> !id.equals(NONE)).count();
  }
}
