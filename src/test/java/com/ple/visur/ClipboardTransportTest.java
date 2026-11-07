package com.ple.visur;

import io.vertx.core.json.JsonObject;
import io.vertx.rxjava3.core.Vertx;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The server never touches an OS clipboard: it asks a client, and the client is the half sitting on the user's machine.
 *
 * <p>Every test here is on the wire, for a reason that outlives the feature — a clipboard write is invisible in the
 * model. Nothing in the document, the session or the view changes when a yank is offered to a client, so the only thing
 * a test can read is the frame the socket was sent. Which is also the only thing a client can get wrong.
 */
class ClipboardTransportTest {

  /** Line starts: 0, 19, 28. */
  private static final String CONTENT = "one two three four\nfive six\n";
  private static final int PORT = 9000;

  private static int nextPort;

  private Vertx vertx;
  private int port;
  private EditorModelCoupler emc;

  @BeforeEach
  void startTheServer() {
    // The port range this class shares with nothing: see ViewportPerClientTest#awaitNoConnections and
    // MainVerticle#couldNotListen for what a collision costs.
    port = PORT + nextPort++;
    System.setProperty("visur.port", String.valueOf(port));
    vertx = Vertx.vertx();
    vertx.deployVerticle(new MainVerticle()).blockingGet();
    ServiceHolder.initializerService.initializeEditorModel();
    emc = ServiceHolder.editorModelCoupler;
    emc.document().setSourceFilePath(""); // no swap files beside the working tree
    emc.document().putContent(CONTENT);
    ViewportPerClientTest.awaitNoConnections();
  }

  @AfterEach
  void stopTheServer() {
    vertx.close().blockingAwait();
  }

  /**
   * A yank asks one window to write its clipboard: the one whose keys made the yank. A second window on the same
   * document is looking at the same words and typed none of them, and writing its user's clipboard too would be two
   * beeps and a second machine's clipboard holding text its user never copied.
   */
  @Test
  void aYankAsksTheWindowThatTypedItToWriteItsClipboard() {
    try (WireClient first = new WireClient(vertx, port, 60, 8);
         WireClient second = new WireClient(vertx, port, 60, 8)) {
      first.presses("w"); // the word quantum, so the selection is a word and not a character
      first.presses("s");
      first.presses("y");

      JsonObject asked = first.nextRequest(BusEvent.clipboardWriteRequested.name(), 2000);
      assertNotNull(asked, "a yank asks a window to write its clipboard");
      assertEquals("one ", asked.getString("text"), "and offers exactly what was yanked");

      assertNull(second.nextRequest(BusEvent.clipboardWriteRequested.name(), 300),
        "a window that typed nothing is not asked to write anything");
    }
  }

  /**
   * Three copies pasted over one selection is one clipboard write.
   *
   * <p>This is the map's "one write per command, never per keystroke" as a client can observe it: every paste request
   * replaces what the user had, and terminals answer a stream of them with beeps. The count multiplies the copies
   * inserted, so it is where a paste could plausibly ask three times.
   */
  @Test
  void threeCopiesOverOneSelectionAreOneClipboardWrite() {
    try (WireClient client = new WireClient(vertx, port, 60, 8)) {
      client.presses("w");
      client.presses("s");
      client.presses("y");
      assertNotNull(client.nextRequest(BusEvent.clipboardWriteRequested.name(), 2000),
        "the yank was one write");

      client.presses("3");
      client.presses("p"); // three copies of "one " over the selection it was already covering

      JsonObject asked = client.nextRequest(BusEvent.clipboardWriteRequested.name(), 2000);
      assertNotNull(asked, "the paste offers what it covered");
      assertEquals("one ", asked.getString("text"), "the text the paste displaced, once — not three copies of it");
      assertNull(client.nextRequest(BusEvent.clipboardWriteRequested.name(), 300),
        "and the three copies asked three times at most");
    }
  }

  /**
   * The server remembers the last thing it asked to be written, which is what makes the read rare: everything copied
   * inside visur was copied by visur, so pasting it back is a lookup rather than a question to a terminal that may not
   * be able to answer one.
   */
  @Test
  void aWriteCachesWhatItSent() {
    try (WireClient client = new WireClient(vertx, port, 60, 8)) {
      client.presses("w");
      client.presses("s");
      client.presses("y");
      assertNotNull(client.nextRequest(BusEvent.clipboardWriteRequested.name(), 2000), "the yank went out");
      assertEquals("one ", Clipboard.cached(), "and the server knows the clipboard holds it now");
    }
  }

  /**
   * A command with no window in front of it writes nobody's clipboard, so it learns nothing about one: the headless
   * server and a test that presses keys into a model are both in this case. Caching here would be the server claiming
   * to know what a clipboard holds because it thought about writing one — and the stale paste the read exists to
   * prevent is exactly the thing on the other side of that claim.
   *
   * <p>The offer is still spent, which is the other half: an offer left behind would go out with the next command, to
   * whichever window typed that one, and the clipboard would hold text its user copied in a command two keys back.
   */
  @Test
  void aCommandWithNoWindowCachesNothing() {
    emc.session().putCursorQuantum(emc.getQuantumNameToQuantum().get("word"));
    emc.session().putCA(0);
    emc.session().putSpan(1);
    emc.session().putCursorQuantumStartAndScroll(0);
    emc.session().putCursorQuantumEndAndScroll(4); // "one " selected, the way `w s` leaves it

    KeyWasPressedVerticle.press(emc, KeyPressed.from("y"), "y");
    KeyWasPressedVerticle.flushClipboard(emc, vertx.eventBus());

    assertNull(Clipboard.cached(), "nothing was sent anywhere, so nothing is known about any clipboard");
    assertNull(emc.session().takeClipboardWrite(), "and the offer was spent, not left for a window that arrives later");
  }

  /**
   * Reading is a question, not a call: the clipboard is on the other end of the socket, and over SSH that is a different
   * machine than the one holding the document. So the server asks the window, and the answer comes back as a frame of
   * its own — which is also why the read is rare and the cache is what makes an ordinary paste need no round trip.
   */
  @Test
  void aWindowThatAnswersBecomesWhatTheServerKnows() throws InterruptedException {
    Clipboard.cache("what visur copied earlier");
    try (WireClient client = new WireClient(vertx, port, 60, 8)) {
      ClipboardContents.ask(vertx, onlyWindow());

      assertNotNull(client.nextRequest(BusEvent.clipboardReadRequested.name(), 2000),
        "the server asks a window what its clipboard holds");
      client.send(BusEvent.clipboardContents.name(), new JsonObject().put("text", "copied outside visur"));

      assertEquals("copied outside visur", awaitCached("copied outside visur"),
        "what the clipboard says it holds is what the server knows");
      assertEquals("", client.view().getString("message"), "and an answer that arrived needs no announcement");
    }
  }

  /**
   * A window that cannot read its own clipboard says so by answering with no text at all — Terminal.app has no OSC 52
   * read, iTerm2 gates it, tmux swallows it, and a client that has to answer in under half a second cannot tell those
   * apart from a clipboard that is merely empty.
   *
   * <p>What it takes with the answer is the memory. The server knew the clipboard held one thing; it has just learned it
   * cannot ask, so from now on that knowledge is a guess, and a guess pasted quietly is the one outcome the map refuses
   * outright. So the failure is both said and paid for: the message, and a cache the next paste cannot lean on.
   */
  @Test
  void aWindowThatCannotAnswerTakesTheStaleMemoryWithIt() {
    Clipboard.cache("what visur copied earlier");
    try (WireClient client = new WireClient(vertx, port, 60, 8)) {
      ClipboardContents.ask(vertx, onlyWindow());

      assertNotNull(client.nextRequest(BusEvent.clipboardReadRequested.name(), 2000), "the question went out");
      client.send(BusEvent.clipboardContents.name(), new JsonObject()); // no text field, which is the failure

      String message = client.view().getString("message");
      assertTrue(message.contains("clipboard"), "the user learns the clipboard is what failed: " + message);
      assertNull(Clipboard.cached(), "and the server stops trusting a memory it can no longer check");
    }
  }

  /**
   * The cache until it says this, because a successful read leaves no trace on the wire for a test to wait on: the
   * answer arrives on the server's thread, and the only thing it changes is a fact in the server's memory. A failed
   * read, by contrast, repaints, so those tests can wait for the frame the way the rest of this class does.
   */
  private static String awaitCached(String expected) throws InterruptedException {
    for (int patience = 0; patience < 200 && !java.util.Objects.equals(expected, Clipboard.cached()); patience++) {
      Thread.sleep(10);
    }
    return Clipboard.cached();
  }

  /**
   * A copy too big for a clipboard is refused, not shortened.
   *
   * <p>Emulators cap an OSC 52 payload and drop or truncate a request that passes the cap, and a clipboard holding the
   * first 74 999 characters of what the user highlighted is data loss with the appearance of success. The refusal costs
   * the copy out and nothing else: the yank itself stands, so {@code p} still works and the words are not gone from
   * visur because they would not fit outside it.
   */
  @Test
  void aYankBiggerThanAClipboardIsRefusedRatherThanShortened(@TempDir Path tempDir) {
    // A path in the temp dir, so the swap this content earns lands there and not beside the working tree.
    emc.document().setSourceFilePath(tempDir.resolve("big.txt").toString());
    String big = "a".repeat(Clipboard.LIMIT_IN_UTF8_BYTES + 1);
    emc.document().putContent(big);

    try (WireClient client = new WireClient(vertx, port, 60, 8)) {
      client.presses("r"); // the document quantum, so the selection is all of it
      client.presses("s");
      client.presses("y");

      assertNull(client.nextRequest(BusEvent.clipboardWriteRequested.name(), 300),
        "nothing is offered: half a clipboard is worse than none");
      String message = client.view().getString("message");
      assertTrue(message.contains("clipboard") && message.contains(String.valueOf(Clipboard.LIMIT_IN_UTF8_BYTES)),
        "the message says what was refused and how big a clipboard is: " + message);
      assertEquals(big, Connections.session(onlyWindow()).getGlobalVar("yankRegister").getVal(),
        "and the yank stands: only the copy out was refused");
    }
  }

  /**
   * The limit counts the bytes the transport carries, not the characters on the keyboard: a UTF-8 character is up to
   * four of each, and 30 000 of them is 60 000 bytes of base64 on the way to an emulator that never asked for them.
   */
  @Test
  void theLimitIsBytesAndNotCharacters(@TempDir Path tempDir) {
    emc.document().setSourceFilePath(tempDir.resolve("wide.txt").toString());
    emc.document().putContent("\u00e9".repeat(Clipboard.LIMIT_IN_UTF8_BYTES / 2 + 1)); // two bytes each, so over

    try (WireClient client = new WireClient(vertx, port, 60, 8)) {
      client.presses("r");
      client.presses("s");
      client.presses("y");

      assertNull(client.nextRequest(BusEvent.clipboardWriteRequested.name(), 300),
        "half the limit in characters is still over it in bytes");
    }
  }

  /**
   * The other way a clipboard fails to answer: not a refusal, which is a frame, but nothing at all. A terminal with no
   * OSC 52 read never sends the failure, so the server's patience is the only thing that ends the question, and what it
   * decides is the same as a refusal — say so, and stop trusting the memory.
   */
  @Test
  void aWindowThatSaysNothingAtAllIsNotGuessedFor() throws InterruptedException {
    Clipboard.cache("what visur copied earlier");
    try (WireClient client = new WireClient(vertx, port, 60, 8)) {
      ClipboardContents.ask(vertx, onlyWindow());
      assertNotNull(client.nextRequest(BusEvent.clipboardReadRequested.name(), 2000), "the question went out");

      assertNull(awaitCached(null), "the patience ran out and took the memory with it");
      assertTrue(client.view().getString("message").contains("clipboard"),
        "and the window is told the clipboard is what gave up");
    }
  }

  /**
   * An empty clipboard is a clipboard that answered. Treating {@code ""} as a failure would turn a real fact into an
   * apology, and treating an absent {@code text} field as {@code ""} would turn a terminal that cannot read anything
   * into one that read nothing — which is the difference between pasting nothing and pasting a guess.
   */
  @Test
  void anEmptyClipboardIsAnAnswerAndNotAFailure() throws InterruptedException {
    Clipboard.cache("what visur copied earlier");
    try (WireClient client = new WireClient(vertx, port, 60, 8)) {
      ClipboardContents.ask(vertx, onlyWindow());
      client.nextRequest(BusEvent.clipboardReadRequested.name(), 2000);
      client.send(BusEvent.clipboardContents.name(), new JsonObject().put("text", ""));

      assertEquals("", awaitCached(""), "the clipboard is now known to hold nothing");
      assertEquals("", client.view().getString("message"), "and a question that was answered has nothing to report");
    }
  }

  /**
   * An answer is for the question that is open, not for the one it happens to follow.
   *
   * <p>The second question is here to make the third answer observable: the second lands, so the wiring works, and the
   * third — sent when nothing is outstanding — has to leave the cache alone. A reply honoured late is how a paste ends
   * up holding text the user copied half a minute ago, into a command that never asked for it.
   */
  @Test
  void anAnswerWithNoQuestionOpenDoesNotBecomeTheClipboard() throws InterruptedException {
    try (WireClient client = new WireClient(vertx, port, 60, 8)) {
      ClipboardContents.ask(vertx, onlyWindow());
      client.nextRequest(BusEvent.clipboardReadRequested.name(), 2000);
      client.send(BusEvent.clipboardContents.name(), new JsonObject().put("text", "the second answer"));
      assertEquals("the second answer", awaitCached("the second answer"), "a reply to an open question lands");

      // Nothing on the wire says this one was received, so it is given longer than the socket takes to deliver a frame.
      client.send(BusEvent.clipboardContents.name(), new JsonObject().put("text", "the third answer"));
      Thread.sleep(250);

      assertEquals("the second answer", Clipboard.cached(),
        "a reply to no question at all is dropped rather than honoured");
    }
  }

  /**
   * The id of the one window connected right now. A client never names itself on the wire — the server mints the name,
   * because a socket that could claim another's id could claim its cursor too — so a test that wants to address the
   * window it opened looks it up where the server keeps it.
   */
  private static String onlyWindow() {
    return Connections.ids().stream().filter(id -> !Connections.NONE.equals(id)).findFirst().orElseThrow();
  }

  /**
   * What a command offers is what its <em>last</em> copy produced, and it is spent by the flush whether or not anything
   * was sent.
   *
   * <p>No key offers twice today — {@code p} displaces once however many copies it inserts — so this is the shape of
   * the promise rather than a bug that exists: the offer is a slot and not a send precisely so that a command which
   * does copy twice arrives once, holding the text it settled on. Spending it is the half that matters: an offer left
   * behind would go out with the <em>next</em> command, to whichever window typed that one.
   */
  @Test
  void aCommandThatOffersTwiceOffersTheLastTextOnce() {
    Session window = emc.session();
    Clipboard.offer(window, "first");
    Clipboard.offer(window, "second");
    assertEquals("second", window.takeClipboardWrite(), "the clipboard ends up holding what the command settled on");
    assertNull(window.takeClipboardWrite(), "and the offer is spent, not left for the next command to send");
  }
}
