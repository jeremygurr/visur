package com.ple.visur;

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
    vertx.eventBus().consumer(BusEvent.modelWasChanged.name(), event -> handleChange());
  }

  public void handleChange() {
    vertx.eventBus().send(BusEvent.viewWasChanged.name(), View.of(emc).render());
  }

}
