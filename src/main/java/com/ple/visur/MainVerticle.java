package com.ple.visur;

import io.vertx.codegen.annotations.Nullable;
import io.vertx.ext.bridge.PermittedOptions;
import io.vertx.ext.web.handler.sockjs.SockJSBridgeOptions;
import io.vertx.rxjava3.core.AbstractVerticle;
import io.vertx.rxjava3.core.http.HttpServerRequest;
import io.vertx.rxjava3.core.http.HttpServerResponse;
import io.vertx.rxjava3.core.eventbus.MessageConsumer;
import io.vertx.rxjava3.core.shareddata.SharedData;
import io.vertx.rxjava3.ext.web.Router;
import io.vertx.rxjava3.ext.web.RoutingContext;
import io.vertx.rxjava3.ext.web.handler.sockjs.SockJSHandler;
import io.vertx.core.json.JsonObject;
import io.vertx.rxjava3.core.net.SocketAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

public class MainVerticle extends AbstractVerticle {

  private static final Logger LOGGER = LoggerFactory.getLogger(MainVerticle.class);

  @Override
  public void start() {
    LOGGER.debug("Starting main verticle");

    final SharedData sharedData = vertx.sharedData();
    ServiceHolder.init(sharedData);

    // The port must not open before the bus consumers exist. `send` to an address with no consumer is dropped
    // silently, so a client that declared its viewport in that window would wait for a view that was never
    // triggered -- which is why every client until now re-sent its declaration on a timer.
    vertx.deployVerticle(new KeyWasPressedVerticle())
      .flatMap(ignored -> vertx.deployVerticle(new ViewportWasChangedVerticle()))
      .flatMap(ignored -> vertx.deployVerticle(new ModelWasChangedVerticle()))
      .subscribe(
        ignored -> vertx.createHttpServer().requestHandler(router()).listen(8888),
        error -> LOGGER.error("could not deploy the verticles", error));
  }

  private Router router() {
    Router router = Router.router(vertx);

    SockJSBridgeOptions opts = new SockJSBridgeOptions()
      .addInboundPermitted(new PermittedOptions().setAddress(BusEvent.keyWasPressed.name()))
      .addInboundPermitted(new PermittedOptions().setAddress(BusEvent.viewportWasChanged.name()))
      .addOutboundPermitted(new PermittedOptions().setAddress(BusEvent.viewWasChanged.name()));

    router.get("/static/*").handler(this::staticHandler);
    router.mountSubRouter("/eventbus", SockJSHandler.create(vertx).bridge(opts));
    router.get("/ws").handler(this::webSocket);

    router.get("/").handler(routingContext -> {
      // Get the response object
      HttpServerResponse response = routingContext.response();

      // Set the HTTP status code to 302 (Found) for a temporary redirect. Note that if I want changes to be cached I can change this to 301 for permanent redirect
      response.setStatusCode(302);

      // Set the "Location" header to the target URL
      response.putHeader("Location", "http://localhost:8888/static/visur.html");

      // End the response to perform the redirect
      response.end();
    });

    return router;
  }

  /**
   * The socket a terminal client uses: plain JSON in, plain JSON out, no SockJS framing and no heartbeat.
   *
   * <p>Frames are the bus envelope in both directions, {@code {"address": …, "body": …}}, because the socket is a
   * pipe to the event bus and nothing more. One client at a time, so one consumer per socket is unambiguous.
   */
  private void webSocket(RoutingContext context) {
    if (!isLoopback(context.request())) {
      // This is a direct line to the editor's event bus; it is not for anything off the machine.
      context.response().setStatusCode(403).end();
      return;
    }
    context.request().toWebSocket().subscribe(
      socket -> {
        MessageConsumer<JsonObject> outbound = vertx.eventBus().consumer(BusEvent.viewWasChanged.name(),
          message -> socket.writeTextMessage(
            new JsonObject().put("address", BusEvent.viewWasChanged.name()).put("body", message.body()).encode()));
        socket.textMessageHandler(frame -> {
          JsonObject message = new JsonObject(frame);
          vertx.eventBus().send(message.getString("address"), message.getValue("body"));
        });
        // Both paths: a socket can fail without ever reporting a close, and a consumer left pointing at a dead
        // socket would keep swallowing published views.
        socket.closeHandler(closed -> outbound.unregister());
        socket.exceptionHandler(failure -> outbound.unregister());
      },
      error -> LOGGER.error("websocket upgrade failed", error));
  }

  private static boolean isLoopback(HttpServerRequest request) {
    SocketAddress address = request.remoteAddress();
    String host = address == null ? null : address.hostAddress();
    return host != null && (host.startsWith("127.") || host.equals("0:0:0:0:0:0:0:1") || host.equals("::1"));
  }

  private void staticHandler(RoutingContext context) {

    final HttpServerResponse response = context.response();
    final HttpServerRequest request = context.request();
    @Nullable
    String path = request.path();

    try {
      LOGGER.debug("GET " + path);
      path = path.substring(1);
      final InputStream stream = Thread.currentThread().getContextClassLoader().getResourceAsStream(path);
      if (stream != null) {
        final String text = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).lines()
          .collect(Collectors.joining("\n"));
        if (path.endsWith(".html")) {
          response.putHeader("Content-Type", "text/html");
        } else if (path.endsWith(".css")) {
          response.putHeader("Content-Type", "text/css");
        } else if (path.endsWith(".js")) {
          response.putHeader("Content-Type", "application/javascript");
        } else {
          response.end("<html><body>Error filetype unknown: " + path + "</body></html>");
        }
        response.setStatusCode(200);
        response.end(text);
      } else {
        LOGGER.warn("Resource not found: " + path);
        response.setStatusCode(404);
        response.end();
      }
    } catch (Exception e) {
      LOGGER.error("Problem fetching static file: " + path, e);
      response.setStatusCode(502);
      response.end();
    }
  }
}
