package com.ple.visur;

import io.vertx.codegen.annotations.Nullable;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.rxjava3.core.AbstractVerticle;
import io.vertx.rxjava3.core.http.HttpServerRequest;
import io.vertx.rxjava3.core.http.HttpServerResponse;
import io.vertx.rxjava3.core.eventbus.MessageConsumer;
import io.vertx.rxjava3.ext.web.Router;
import io.vertx.rxjava3.ext.web.RoutingContext;
import io.vertx.core.json.JsonObject;
import io.vertx.rxjava3.core.net.SocketAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class MainVerticle extends AbstractVerticle {

  private static final Logger LOGGER = LoggerFactory.getLogger(MainVerticle.class);
  private static final AtomicInteger nextConnection = new AtomicInteger(1);

  /**
   * Started is not "the verticle was constructed": the port only counts as open once the bus consumers behind it
   * exist and are listening.
   *
   * <p>{@code send} to an address with no consumer is dropped silently, so a client that declared its viewport in the
   * gap would wait for a view that was never triggered — which is why every client until now re-sent its declaration
   * on a timer. The same gap is what made a test that deploys this verticle, connects, and presses a key lose a race
   * it wins only when the machine is idle.
   */
  @Override
  public void start(io.vertx.core.Promise<Void> started) {
    LOGGER.debug("Starting main verticle");

    ServiceHolder.init();

    vertx.deployVerticle(new KeyWasPressedVerticle())
      .flatMap(ignored -> vertx.deployVerticle(new ViewportWasChangedVerticle()))
      .flatMap(ignored -> vertx.deployVerticle(new ModelWasChangedVerticle()))
      .subscribe(
        ignored -> vertx.createHttpServer().requestHandler(router()).listen(serverPort())
          .subscribe(listening -> started.complete(), failure -> couldNotListen(failure, started)),
        error -> {
          LOGGER.error("could not deploy the verticles", error);
          started.fail(error);
        });
  }

  /**
   * A server that is not listening is not a server, and the alternative is a process that never serves anybody and
   * never says why: {@code listen} completes successfully for the verticle either way, so the failure used to vanish
   * into a debug log while the JVM sat there holding a port it did not have. Orphan servers piled up that way, and
   * tests failed by connecting to somebody else's server.
   */
  private void couldNotListen(Throwable failure, io.vertx.core.Promise<Void> started) {
    System.err.printf("visur cannot serve on port %d: %s%n", serverPort(), String.valueOf(failure.getMessage()));
    started.fail(failure);
    Runtime.getRuntime().halt(1);
  }

  @Override
  public void stop() {
    // A socket dropped by a shutdown never reports a close, so this is the difference between a connection registry
    // that lives as long as the server and one that outlives it.
    Connections.closeAll();
  }

  private static int serverPort() {
    // A property in front of the environment variable because a test cannot set an environment variable without
    // spawning a process, and this port is the one thing a test cannot share with a developer's running server.
    return Integer.getInteger("visur.port", Integer.parseInt(System.getenv().getOrDefault("VISUR_PORT", "8888")));
  }

  private Router router() {
    Router router = Router.router(vertx);

    // One transport for every client, browser included. There used to be a SockJS bridge at /eventbus for the page,
    // which cost the page two scripts from a CDN and cost the server its only unauthenticated way in: the bridge had
    // no loopback check, and it could not carry a connection id, which is the thing a per-client viewport is made of.
    router.get("/static/*").handler(this::staticHandler);
    router.get("/ws").handler(this::webSocket);

    router.get("/").handler(routingContext -> {
      // Get the response object
      HttpServerResponse response = routingContext.response();

      // Set the HTTP status code to 302 (Found) for a temporary redirect. Note that if I want changes to be cached I can change this to 301 for permanent redirect
      response.setStatusCode(302);

      // Relative, so the page and the socket it opens are on whatever port this server actually took.
      response.putHeader("Location", "/static/visur.html");

      // End the response to perform the redirect
      response.end();
    });

    return router;
  }

  /**
   * The socket every client uses, terminal and browser alike: plain JSON in, plain JSON out, no framing and no
   * heartbeat.
   *
   * <p>Frames are the bus envelope in both directions, {@code {"address": …, "body": …}}, because the socket is a
   * pipe to the event bus and nothing more. Accepting one is also how a connection gets its name: everything the
   * client sends is stamped with the id minted here, and every view sent back is stamped with it, which is the only
   * way this server knows whose window a set of rows was laid out for. See {@link Connections}.
   */
  private void webSocket(RoutingContext context) {
    if (!isLoopback(context.request())) {
      // This is a direct line to the editor's event bus; it is not for anything off the machine.
      context.response().setStatusCode(403).end();
      return;
    }
    context.request().toWebSocket().subscribe(
      socket -> {
        // The server names the connection, because that is the only name in the exchange worth believing: it is what
        // every later message is attributed to, including the one that says how big the window is.
        String connection = "ws-" + nextConnection.getAndIncrement();
        // A window arriving is two things: the window gets its own cursor and quantum, and it is asked to open the file
        // the process was launched on, if anybody asked for one. See InitializerService#openLaunchFile for why that
        // waits for an arrival instead of happening at boot.
        Session window = Connections.open(connection);
        ServiceHolder.initializerService.startSession(window);
        ServiceHolder.initializerService.openLaunchFile(window);
        MessageConsumer<JsonObject> outbound = vertx.eventBus().consumer(BusEvent.viewWasChanged.name(), message -> {
          // Published to everybody, painted for one: the view whose header does not name this socket was rendered at
          // some other client's width, and drawing it here is the bug this whole class of one-field-per-client fixed.
          if (!connection.equals(message.headers().get(Connections.HEADER))) {
            return;
          }
          socket.writeTextMessage(
            new JsonObject().put("address", BusEvent.viewWasChanged.name()).put("body", message.body()).encode());
        });
        MessageConsumer<JsonObject> farewell = vertx.eventBus().consumer(BusEvent.connectionShouldClose.name(), message -> {
          // The view's addressing again: this socket closes the window whose id it minted, and no other. `:q` typed in
          // one client ending a second client's session would be the same bug this connection id was introduced to fix.
          if(!connection.equals(message.headers().get(Connections.HEADER))) {
            return;
          }
          // A normal close, deliberately: the client tells "you asked to leave" apart from "the server died" by the
          // status code, and only the first of them may tell the launcher to leave the server running.
          socket.close((short) 1000, ":q");
        });
        socket.textMessageHandler(frame -> {
          JsonObject message = new JsonObject(frame);
          vertx.eventBus().send(message.getString("address"), message.getValue("body"),
            new DeliveryOptions().addHeader(Connections.HEADER, connection));
        });
        // Both paths: a socket can fail without ever reporting a close, and a consumer left pointing at a dead
        // socket would keep swallowing published views. The connection goes with the socket either way, which is
        // what stops a day of browser reloads from leaving a day of viewports behind.
        socket.closeHandler(closed -> {
          outbound.unregister();
          farewell.unregister();
          Connections.close(connection);
        });
        socket.exceptionHandler(failure -> {
          outbound.unregister();
          farewell.unregister();
          Connections.close(connection);
        });
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
