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
    Connections.resize(Connections.headerOf(event), viewport.getInteger("cols"), viewport.getInteger("rows"));
    // Every row boundary moved, so a renderer that diffs must not compare against what it had.
    bus.send(BusEvent.modelWasChanged.name(), new JsonObject().put("full", true));
  }

}
