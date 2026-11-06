package com.ple.visur;

import io.vertx.rxjava3.core.Vertx;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Stack;

import static com.ple.visur.EditorSubmode.navigate;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Two pairs of hands, one document.
 *
 * <p>Ticket 03's split is between the text and the hands holding it, and the only test that can tell a split from a
 * renaming is one with two of the latter looking at one of the former. Every assertion here says the same thing twice:
 * what one window did, and what the other window still shows.
 */
class SessionsOnOneDocumentTest {

  private static final String CONTENT = "aaaabbbbccccddddeeeeffff\n";
  private static final int PORT = 8950;

  private static int nextPort;

  private Vertx vertx;
  private int port;
  private EditorModelCoupler emc;

  @BeforeEach
  void startTheServer() {
    // The port is a system property rather than an environment variable so a test can set it without spawning a
    // process, and it is a port a developer's `visur` is not sitting on. Per test rather than per class: a Vert.x
    // close that has completed does not always hand the listening socket back in time for the next test in this JVM
    // to bind it, and a server that could not bind used to start anyway and answer nothing.
    // See MainVerticle.couldNotListen.
    port = PORT + nextPort++;
    System.setProperty("visur.port", String.valueOf(port));
    vertx = Vertx.vertx();
    vertx.deployVerticle(new MainVerticle()).blockingGet();
    ServiceHolder.initializerService.initializeEditorModel();
    emc = ServiceHolder.editorModelCoupler;
    emc.document().setSourceFilePath("");
    emc.document().putContent(CONTENT);
    emc.session().putCanvasStart(0);
    emc.session().putCA(0);
    Stack<EditorSubmode> submodes = new Stack<>();
    submodes.push(navigate);
    emc.session().putEditorSubmodeStack(submodes);
    emc.session().putIsInCommandState(false);
    ViewportPerClientTest.awaitNoConnections();
  }

  @AfterEach
  void stopTheServer() {
    // Blocking, so the server is gone before the next test asks for the port range back.
    vertx.close().blockingAwait();
  }

  /**
   * Whether the text has unsaved work is the document's answer, so a window that typed nothing is warned anyway —
   * and one write takes the warning off both, because after it neither pair of hands holds anything the file lacks.
   *
   * <p>The typing is a real insert session and the save is the real key, because that sequence is where the marker
   * earns its keep: leaving insert mode hands the command's scratch stack back, and a save that reads an argument
   * out of it was reading the previous command's leftovers. A document {@code putContentAsSaved} is the ticket's
   * "loaded from disk is clean", and the `x` is the ticket's "one character typed is dirty".
   */
  @Test
  void theUnsavedMarkerShowsInEveryWindowAndOneSaveClearsItEverywhere(@TempDir Path tempDir) throws IOException {
    Path file = tempDir.resolve("work.txt");
    emc.document().setSourceFilePath(file.toString());
    emc.document().putContentAsSaved(CONTENT);

    try (WireClient first = new WireClient(vertx, port, 40, 10);
         WireClient second = new WireClient(vertx, port, 40, 10)) {
      assertFalse(first.isDirty(), "the text is the file's text, so there is nothing to warn about");
      assertFalse(second.isDirty(), "and neither window has anything the other does not");

      first.presses("i");
      first.types("x");
      first.presses("Escape");

      assertTrue(first.isDirty(), "this window typed a character");
      assertTrue(second.isDirty(), "and the window that watched is warned too: the work is the document's");

      first.presses("ZZ");

      // Both windows before the file: reading a view is what waits for the save to have been handled, since a frame
      // is rendered after the command runs. Asserting the file first would race the bus.
      boolean firstAfter = first.isDirty();
      boolean secondAfter = second.isDirty();

      assertEquals("x" + CONTENT, Files.readString(file), "the save wrote what one window typed");
      assertFalse(firstAfter, "the window that saved is clean");
      assertFalse(secondAfter, "and so is the window that only watched");
    }
  }

  @Test
  void whatIsBeingTypedInInsertModeBelongsToTheWindowTypingIt() {
    try (WireClient first = new WireClient(vertx, port, 40, 10);
         WireClient second = new WireClient(vertx, port, 40, 10)) {
      // One window is mid-insert with a count of three. It has typed "ab" and not left insert mode.
      first.presses("3");
      first.presses("i");
      first.types("ab");

      // The other window opens its own insert with no count at all. A count that lives in the shared model would
      // pay out here: three copies of this character, because the only count in the editor belongs to the other
      // window, and the typed text it repeats would be "ab" rather than "z".
      second.presses("i");
      second.presses("z");
      second.presses("Escape");

      assertEquals(1, occurrences(second.text(), "z"), "one copy: this window typed one character with no count");

      // ...and the count is still owed where it was typed: two copies of "ab" remain of the three, one having been
      // typed. Length rather than a count of "ab", because the copies land inside the run they repeat.
      int charactersBefore = second.text().length();
      first.presses("Escape");
      assertEquals(charactersBefore + 4, first.text().length(),
        "the first window still gets the copies its own count owes");
      assertEquals(1, occurrences(first.text(), "z"), "and the other window's typing is not this window's to repeat");
    }
  }

  static int occurrences(String haystack, String needle) {
    int found = 0;
    for (int at = haystack.indexOf(needle); at >= 0; at = haystack.indexOf(needle, at + 1)) {
      found++;
    }
    return found;
  }

  @Test
  void oneDocumentIsOneDocument() {
    try (WireClient first = new WireClient(vertx, port, 40, 10);
         WireClient second = new WireClient(vertx, port, 40, 10)) {
      // "zz" typed in one window is in the other window's text: two sessions, one document, not two copies.
      first.presses("i");
      first.types("zz");
      first.presses("Escape");
      assertTrue(second.view().toString().contains("zz"), "the edit is in the document both windows are looking at");

      // ...and one undo ring, because undo is a property of the text and not of who changed it. The window that
      // never typed undoes what the other one did.
      second.presses("u");
      assertEquals(false, first.view().toString().contains("zz"),
        "the neighbour's undo took the edit out from under both windows");
    }
  }

  @Test
  void aMessageIsShownToTheWindowThatEarnedIt() {
    emc.document().putContentAsSaved(CONTENT); // a load over unsaved work is refused, and this is the load that fails
    try (WireClient first = new WireClient(vertx, port, 60, 10);
         WireClient second = new WireClient(vertx, port, 60, 10)) {
      assertEquals("", second.message(), "nothing has gone wrong yet, in either window");

      // A load of a file that is not there, because it is the shortest thing that is certain to fail and say so.
      first.presses(":"); // the door is not part of the command
      first.types("open /it/is/not/here");
      first.presses("Enter");

      assertTrue(first.message().contains("not found"), "the window that asked for the file was told it is not there");
      assertEquals("", second.message(), "the other window did not ask, and is not being told about it");
    }
  }

  @Test
  void aCommandLineBelongsToTheWindowThatOpenedIt() {
    try (WireClient first = new WireClient(vertx, port, 40, 10);
         WireClient second = new WireClient(vertx, port, 40, 10)) {
      assertEquals(false, second.isInCommandState(), "neither window is being asked for a command");

      first.presses(":");
      assertEquals(true, first.isInCommandState(), "the window that pressed : is being asked for a command");
      assertEquals(false, second.isInCommandState(), "the other window was not asked, and is still editing");
    }
  }

  @Test
  void aSubmodeBelongsToTheWindowThatEnteredIt() {
    try (WireClient first = new WireClient(vertx, port, 40, 10);
         WireClient second = new WireClient(vertx, port, 40, 10)) {
      assertEquals("navigate", second.submode(), "both windows start in navigate");

      first.presses("[");
      assertEquals("quantumStart", first.submode(), "the window that pressed [ is choosing where its range starts");
      assertEquals("navigate", second.submode(), "the other window is not being asked to choose anything");
    }
  }

  @Test
  void aScrolledWindowDoesNotScrollItsNeighbour() {
    // Ten columns by two rows for twenty-four characters: three wrapped rows will not fit, so a window that follows
    // its own cursor off the bottom has to scroll sideways along the line to keep it in sight.
    try (WireClient first = new WireClient(vertx, port, 10, 2);
         WireClient second = new WireClient(vertx, port, 10, 2)) {
      assertEquals(0, second.origin(), "both windows start at the beginning of the document");

      for (int step = 0; step < 22; step++) {
        first.presses("l");
      }

      assertTrue(first.origin() > 0, "the window that followed its cursor scrolled along the line");
      assertEquals(0, second.origin(), "the window that stayed home did not go with it");
    }
  }

  @Test
  void aCursorBelongsToTheWindowThatMovedIt() {
    try (WireClient first = new WireClient(vertx, port, 40, 10);
         WireClient second = new WireClient(vertx, port, 40, 10)) {
      assertEquals(0, second.cursorQuantumStart(), "both windows start at the beginning");

      first.presses("l");
      first.presses("l");
      first.presses("l");
      assertEquals(3, first.cursorQuantumStart(), "the window that moved right three times is three characters in");
      assertEquals(0, second.cursorQuantumStart(), "the other window has not moved, and will not until its own keys arrive");

      // Not just where the cursor is: what the cursor *is*. One window moving a word at a time while the other is
      // still moving a character at a time is the whole reason the quantum cannot be a property of the document.
      first.presses("w");
      assertEquals("word", first.cursorQuantum(), "the window that pressed w moves words now");
      assertEquals("character", second.cursorQuantum(), "the other window never asked for that, and is not moving words");
    }
  }

  @Test
  void aSelectionBelongsToTheWindowThatMadeIt() {
    try (WireClient first = new WireClient(vertx, port, 40, 10);
         WireClient second = new WireClient(vertx, port, 40, 10)) {
      // A window that joins selects the quantum its cursor is on, exactly as the first window does, so the
      // interesting baseline is whatever the neighbour was already showing rather than a number from the spec.
      int untouched = second.span();

      first.presses("5");
      first.presses("s");
      assertEquals(5, first.span(), "the window that asked for five quantums has five selected");
      assertEquals(untouched, second.span(), "the other window is still showing what it showed before its neighbour selected");
    }
  }

  @Test
  void aTypedCountBelongsToTheWindowThatTypedIt() {
    try (WireClient first = new WireClient(vertx, port, 40, 10);
         WireClient second = new WireClient(vertx, port, 40, 10)) {
      first.presses("5");
      assertEquals(5, first.pendingCount(), "the window that typed 5 is counting 5");

      second.presses("3");
      assertEquals(3, second.pendingCount(), "the window that typed 3 is counting 3");
      assertEquals(5, first.pendingCount(), "and the first window is still counting what its own hands typed");
    }
  }
}
