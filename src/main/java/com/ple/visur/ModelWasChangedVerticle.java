package com.ple.visur;

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
    vertx.eventBus().send(BusEvent.viewWasChanged.name(), View.of(emc, isFull(event.body())).render());
  }

  private static boolean isFull(Object trigger) {
    return trigger instanceof JsonObject json && Boolean.TRUE.equals(json.getBoolean("full"));
  }

}
