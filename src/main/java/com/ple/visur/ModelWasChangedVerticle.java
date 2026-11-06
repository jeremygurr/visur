package com.ple.visur;

import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.rxjava3.core.eventbus.Message;

/**
 * Turns "the model changed" into "here is the view".
 *
 * <p>A pipe, deliberately. It used to own a single mutable {@link View} instance, rewritten and re-sent on every
 * message, which made two models' worth of state (the editor's and this verticle's copy) that could disagree;
 * and it serialised the payload itself, so the wire format lived in a verticle. Both are now someone else's job:
 * {@link View#of(EditorModelCoupler)} projects and {@link View#render()} serialises.
 */
public class ModelWasChangedVerticle extends AbstractVisurVerticle {

  EditorModelCoupler emc = ServiceHolder.editorModelCoupler;

  @Override
  public void start() {
    vertx.eventBus().consumer(BusEvent.modelWasChanged.name(), this::handleChange);
  }

  /** The trigger may ask for a full repaint; a keystroke changes no layout, so it does not. */
  public void handleChange(Message<Object> event) {
    // One render per connection, because what a document looks like depends on the grid it is being painted into:
    // the rows, where they start, the cursor's column and the clip of the mode line are all width's business. Two
    // clients means two renders and three means three; at the number of clients a text editor has, rendering is
    // cheaper than reasoning about a cache of them would be.
    boolean full = isFull(event.body());
    for (String connection : Connections.ids()) {
      // The same adoption a keypress does, so the render sees this connection's geometry and nothing downstream of
      // View.of has to be told which client it is drawing for.
      emc.putConnection(connection);
      // publish, not send: a view is state addressed to every renderer, not a job for one of them. `send` picks a
      // single consumer per message, so a renderer attached a moment ago -- or one whose socket died without
      // unregistering -- silently swallows the views meant for the others. The header says whose copy it is; a
      // socket forwards it to its client only when that header names it, and drops the rest.
      vertx.eventBus().publish(BusEvent.viewWasChanged.name(), View.of(emc, full).render(),
        new DeliveryOptions().addHeader(Connections.HEADER, connection));
    }
  }

  private static boolean isFull(Object trigger) {
    return trigger instanceof JsonObject json && Boolean.TRUE.equals(json.getBoolean("full"));
  }

}
