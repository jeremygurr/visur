package com.ple.visur;

import io.vertx.core.json.JsonObject;
import io.vertx.rxjava3.core.eventbus.Message;

/**
 * A client declared the content grid it can paint.
 *
 * <p>Units are cells, and the declared rows are content rows: the client subtracts its own chrome. Nothing here
 * branches on which interface is attached — the browser and a terminal send the same message and get the same
 * recomputation, because behaviour does not differ between them. What does differ is whose grid it is: the declaration
 * resizes the connection that sent it, and no other.
 *
 * <p>It also carries the one word that says what the client is ({@code terminal}, {@code browser}), which decides
 * nothing about editing and one thing about where an unsaved nameless buffer's swap is claimed from: a terminal was
 * launched inside a directory, and a browser tab is not inside anything. Absent or unrecognised is the same answer,
 * which is no swap rather than a guess at a directory.
 *
 * <p>It used to print every line of the document on every resize, which made a window drag produce hundreds of
 * lines of log and told nobody anything.
 */
public class ViewportWasChangedVerticle extends AbstractVisurVerticle {

  @Override
  public void start() {
    vertx.eventBus().consumer(BusEvent.viewportWasChanged.name(), this::handle);
  }

  public void handle(Message<Object> event) {
    JsonObject viewport = jsonBody(event);
    // The id comes from the socket that relayed this frame, not from the frame: the client that resized is whoever
    // the server accepted the connection as, and a client cannot say "apply this to the other window".
    String connection = Connections.headerOf(event);
    Connections.resize(connection, viewport.getInteger("cols"), viewport.getInteger("rows"));
    ClientKind kind = ClientKind.parse(viewport.getString("client"));
    if (kind != null) {
      // Only a word we know replaces what is already there: a client that resizes without re-declaring itself must not
      // talk itself out of the directory its swaps are already being written into.
      Connections.session(connection).declareClientKind(kind);
    }
    // Every row boundary moved, so a renderer that diffs must not compare against what it had.
    bus.send(BusEvent.modelWasChanged.name(), new JsonObject().put("full", true));
  }

}
