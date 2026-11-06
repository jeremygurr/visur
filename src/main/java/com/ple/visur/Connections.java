package com.ple.visur;

import io.vertx.rxjava3.core.eventbus.Message;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Who is attached to the server, and what each of them is doing: one {@link Session} per open connection.
 *
 * <p>A session holds the window's geometry, and geometry is what made this class necessary: {@code canvasWidth} and
 * {@code canvasHeight} used to be two fields in the shared editor model, so the second client to resize decided how the
 * first one's lines wrapped. Geometry is a property of a connection, not of a document, and this class is the only
 * place it — or anything else a window owns — is kept.
 *
 * <p>A connection is a socket, and its id is minted by the server when the socket is accepted (see
 * {@link MainVerticle#webSocket}). Clients never name themselves: a socket that could claim another's id could claim
 * its cursor too, and {@code viewportWasChanged} would be a way to resize someone else's window.
 *
 * <p>Static, unlike the document, because it is transport state rather than editor state: {@link MainVerticle}'s socket
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

  /** One client's content grid, in cells. Rows are content rows: the client subtracts its own chrome. */
  public record Viewport(int cols, int rows) {
  }

  private static final Map<String, Session> byId = new ConcurrentHashMap<>();

  private Connections() {
  }

  /** The id on a message, or {@link #NONE} when it came from something that is not a connection we minted. */
  public static String headerOf(Message<?> message) {
    String id = message.headers().get(HEADER);
    return id == null ? NONE : id;
  }

  /**
   * Whose hands these are, starting one if nobody asked before.
   *
   * <p>{@link #NONE} is a session like any other and lives in the same map: the headless server, the pty test and the
   * unit test that never opened a socket all need a session to run against, and giving them a shared one is exactly
   * what the single-session model used to be, for exactly the one client they are.
   */
  public static Session session(String id) {
    Session known = byId.get(id);
    if (known != null) {
      return known;
    }
    // Not in the registry means one of two things. It is the connection of something that never opened a socket — the
    // headless server, a pty test, a unit test — and that is one stable session, shared by exactly the one client they
    // are. Or it is a connection that has already closed, and this is a render or a message that lost the race with
    // the close; such a session is handed out to be written to and thrown away, because remembering one for a socket
    // that no longer exists is how a registry grows by one per dropped connection. That race is not hypothetical: a
    // repaint snapshots the open ids, and a socket can close while it is between reading the id and rendering it.
    if (NONE.equals(id)) {
      return byId.computeIfAbsent(NONE, unused -> new Session());
    }
    return new Session();
  }

  /** A socket was accepted. It gets the default grid until it declares its own. */
  public static Session open(String id) {
    Session session = new Session();
    byId.put(id, session);
    return session;
  }

  /** A socket closed. Its session goes with it, which is what keeps this map from growing by one per browser reload. */
  public static void close(String id) {
    byId.remove(id);
  }

  public static void resize(String id, int cols, int rows) {
    session(id).resize(cols, rows);
  }

  /** Every open connection, including {@link #NONE}: a view has to be rendered for each of them. */
  public static Set<String> ids() {
    Set<String> ids = ConcurrentHashMap.newKeySet();
    ids.addAll(byId.keySet());
    ids.add(NONE);
    return ids;
  }

  /**
   * Forget everybody.
   *
   * <p>A server that has stopped is not serving anybody, and a socket that died with it reports no close, so this is
   * the only thing that keeps a test JVM running one server after another from accumulating the sessions of servers
   * that are gone.
   */
  public static void closeAll() {
    byId.clear();
  }

  /** How many sockets are being remembered. Zero means nothing has accumulated. */
  public static int count() {
    return (int) byId.keySet().stream().filter(id -> !id.equals(NONE)).count();
  }
}
