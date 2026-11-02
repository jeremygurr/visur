package com.ple.visur;

import io.vertx.core.json.JsonObject;
import io.vertx.rxjava3.core.eventbus.Message;

/**
 * A client declared the content grid it can paint.
 *
 * <p>Units are cells, and the declared rows are content rows: the client subtracts its own chrome. Nothing here
 * branches on which interface is attached — the browser and a terminal send the same message and get the same
 * recomputation, because behaviour does not differ between them.
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
    EditorModelCoupler emc = ServiceHolder.editorModelCoupler;
    emc.putCanvasWidth(viewport.getInteger("cols"));
    emc.putCanvasHeight(viewport.getInteger("rows"));
    // Every row boundary moved, so a renderer that diffs must not compare against what it had.
    bus.send(BusEvent.modelWasChanged.name(), new JsonObject().put("full", true));
  }

}
