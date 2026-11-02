package com.ple.visur;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.rxjava3.core.AbstractVerticle;
import io.vertx.rxjava3.core.eventbus.EventBus;
import io.vertx.rxjava3.core.eventbus.Message;

public abstract class AbstractVisurVerticle extends AbstractVerticle {
  final EventBus bus;

  AbstractVisurVerticle() {
    init(Vertx.currentContext().owner(), Vertx.currentContext());
    bus = vertx.eventBus();
  }

  /** The same message arrives as a JsonObject from /ws and as a JSON string from the SockJS bridge. */
  static JsonObject jsonBody(Message<?> message) {
    Object body = message.body();
    return body instanceof JsonObject json ? json : new JsonObject((String) body);
  }

}
