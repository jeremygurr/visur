package com.ple.visur;

import io.vertx.codegen.annotations.Nullable;
import io.vertx.ext.bridge.PermittedOptions;
import io.vertx.ext.web.handler.sockjs.SockJSBridgeOptions;
import io.vertx.rxjava3.core.AbstractVerticle;
import io.vertx.rxjava3.core.http.HttpServerRequest;
import io.vertx.rxjava3.core.http.HttpServerResponse;
import io.vertx.rxjava3.core.shareddata.SharedData;
import io.vertx.rxjava3.ext.web.Router;
import io.vertx.rxjava3.ext.web.RoutingContext;
import io.vertx.rxjava3.ext.web.handler.sockjs.SockJSHandler;
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

    vertx.deployVerticle(new KeyWasPressedVerticle());

    vertx.deployVerticle(new CanvasWasChangedVerticle());

    vertx.deployVerticle(new ModelWasChangedVerticle());


    Router router = Router.router(vertx);

    SockJSBridgeOptions opts = new SockJSBridgeOptions()
      .addInboundPermitted(new PermittedOptions().setAddress(BusEvent.keyWasPressed.name()))
      .addInboundPermitted(new PermittedOptions().setAddress(BusEvent.canvasWasChanged.name()))
      .addInboundPermitted(new PermittedOptions().setAddress(BusEvent.modelWasChanged.name()))
      .addOutboundPermitted(new PermittedOptions().setAddress(BusEvent.modelWasChanged.name()))
      .addOutboundPermitted(new PermittedOptions().setAddress(BusEvent.viewWasChanged.name()));

    router.get("/static/*").handler(this::staticHandler);
    router.mountSubRouter("/eventbus", SockJSHandler.create(vertx).bridge(opts));

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

    vertx.createHttpServer().requestHandler(router).listen(8888);

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
