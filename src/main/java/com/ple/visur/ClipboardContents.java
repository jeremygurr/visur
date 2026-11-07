package com.ple.visur;

import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.rxjava3.core.Vertx;
import io.vertx.rxjava3.core.eventbus.EventBus;
import io.vertx.rxjava3.core.eventbus.Message;

/**
 * The clipboard read: the server's question and the client's answer, in one place.
 *
 * <p>The server cannot know what a clipboard holds — it is on the machine doing the typing, which over SSH is a
 * different machine than the one holding the document. So this class asks, and the answer comes back later as a frame of
 * its own. That shape is the whole design: nothing here waits, because the thread that would wait is the same thread that
 * has to deliver the answer.
 *
 * <p>One question outstanding per window, and no id on the question: what makes a reply valid is that the window still
 * holds the patience timer the question started, and an answer and that timer race for the slot — whoever takes it wins
 * and the other has nothing to do. A reply arriving after its own patience ran out is a reply to nothing, and pasting
 * one is how a window ends up holding text the user copied half a minute ago.
 */
public class ClipboardContents extends AbstractVisurVerticle {

  /**
   * How long a window gets to answer. Half a second is out of a person's way and longer than a terminal needs to say it
   * has nothing: the client's own read of the terminal's reply is shorter than this, so a client that is going to fail
   * has usually failed before the server gives up, and what the user reads is the client's honest no rather than the
   * server's guess about a slow one.
   */
  static final long PATIENCE_IN_MILLIS = 500;

  /** What the user is told when the clipboard could not be read, and what to do instead of waiting for it. */
  private static final String CANNOT_READ = "clipboard: cannot read it; paste with your terminal's own key";

  /**
   * Ask one window what its clipboard holds, replacing a question it has not answered yet.
   *
   * <p>Static and handed a {@link Vertx} because the answer is not wanted by whoever asks for it: an operator marks the
   * need, the command ends, and this runs with the window's own socket in hand — the same shape as a clipboard write,
   * which also waits for the end of the command. The timer goes in before the question is published, because a client
   * that can answer does so immediately and an answer with no question outstanding is thrown away.
   */
  static void ask(Vertx vertx, String connection) {
    Session window = Connections.session(connection);
    Long unanswered = window.takeClipboardReadTimer();
    if (unanswered != null) {
      vertx.cancelTimer(unanswered);
    }
    window.putClipboardReadTimer(vertx.setTimer(PATIENCE_IN_MILLIS, fired -> ranOut(vertx, fired, window)));
    vertx.eventBus().publish(BusEvent.clipboardReadRequested.name(), new JsonObject(),
      new DeliveryOptions().addHeader(Connections.HEADER, connection));
  }

  /** The patience ran out. If an answer took the slot first, there is nothing left to say. */
  private static void ranOut(Vertx vertx, Long firedTimer, Session window) {
    if (!window.clearClipboardReadTimerIf(firedTimer)) {
      return;
    }
    couldNotRead(window, vertx.eventBus());
  }

  @Override
  public void start() {
    vertx.eventBus().consumer(BusEvent.clipboardContents.name(), this::handleReply);
  }

  /**
   * A window's answer to the one question this class asked it.
   *
   * <p>{@code text} present is an answer, {@code ""} included — a clipboard holding nothing is a clipboard that
   * answered. No {@code text} field at all is the failure: the client tried and could not, which is a thing only the
   * client knows, since Terminal.app has no OSC 52 read, iTerm2 gates it behind a preference, and tmux swallows it.
   */
  private void handleReply(Message<Object> event) {
    String connection = Connections.headerOf(event);
    Session window = Connections.session(connection);
    Long waiting = window.takeClipboardReadTimer();
    if (waiting == null) {
      // Nothing is waiting on this window. A reply to a question that already ran out is the ordinary way to get here,
      // and it is dropped rather than honoured: the ask it would have answered is not the ask that is still open.
      return;
    }
    vertx.cancelTimer(waiting);
    JsonObject body = jsonBody(event);
    if (!body.containsKey("text")) {
      couldNotRead(window, bus);
      return;
    }
    Clipboard.cache(body.getString("text"));
  }

  /**
   * The clipboard is unknown, and so the memory of it goes: the server just learned it cannot check, and a value it
   * cannot check is a guess. A paste that quietly takes a guess is the outcome the whole read path was built to make
   * impossible, so the failure is paid for here rather than at the paste.
   *
   * <p>The repaint is part of the failure, not a courtesy: a message the model holds and the screen never drew is a
   * failure nobody was told about, which is the bug this whole feature is a reply to.
   */
  private static void couldNotRead(Session window, EventBus bus) {
    Clipboard.cache(null);
    window.reportError(CANNOT_READ);
    bus.send(BusEvent.modelWasChanged.name(), null);
  }
}
