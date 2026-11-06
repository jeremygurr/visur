package com.ple.visur;

import io.vertx.rxjava3.core.Vertx;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Stack;

import static com.ple.visur.EditorSubmode.navigate;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * How long a message the user was told about stays on screen: until they ask for something else.
 *
 * <p>Both halves are load-bearing and they pull against each other. A message must outlive the keypress that reported
 * it, or it is drawn and erased in the same frame and nobody sees it; and it must not outlive the next one, or the
 * screen is a museum of stale complaints, including the one that says a save failed when the retry saved fine.
 *
 * <p>Driven from the keypress seam rather than by calling {@code reportError}, because which end of a keypress clears
 * the message is exactly the thing a unit test of the model could get wrong and never notice.
 */
class ErrorMessageTest {
  private static final String CONTENT = "one two three four five\n";

  private Vertx vertx;
  private EditorModelCoupler emc;

  @TempDir
  Path tempDir;

  @BeforeEach
  void setUp() {
    vertx = Vertx.vertx();
    ServiceHolder.init();
    ServiceHolder.initializerService.initializeEditorModel();
    emc = ServiceHolder.editorModelCoupler;
    emc.document().setSourceFilePath("");
    emc.document().putContentAsSaved(CONTENT); // a document with unsaved work would be refused rather than failed
    emc.session().putCanvasWidth(80);
    emc.session().putCanvasHeight(24);
    emc.session().putCanvasStart(0);
    emc.session().putCA(0);
    Stack<EditorSubmode> submodes = new Stack<>();
    submodes.push(navigate);
    emc.session().putEditorSubmodeStack(submodes);
    emc.session().putIsInCommandState(false);
  }

  @AfterEach
  void tearDown() {
    vertx.close();
  }

  private void type(String keys) {
    for (String key : keys.split("")) {
      KeyWasPressedVerticle.press(emc, KeyPressed.from(key), key);
    }
  }

  /**
   * The way a person loads a file: `:open`, typed. It used to type the raw sentence `;"path" loadFile`, which is how
   * this test kept passing after the sentence stopped compiling at all — it was testing the operator through a door no
   * user has.
   */
  private void load(String path) {
    type(":open " + path);
    KeyWasPressedVerticle.press(emc, KeyPressed.from("Enter"), "Enter");
  }

  @Test
  void aFailedLoadSaysWhyOnTheScreenAndTheNextKeypressTakesTheComplaintAway() {
    String missing = tempDir.resolve("gone.txt").toString();
    assertEquals("", View.of(emc).render().getString("message"), "nothing has gone wrong yet");

    load(missing);

    assertEquals("loadFile: file not found: " + missing, View.of(emc).render().getString("message"),
      "the key that failed says why, and is still on screen when it is drawn");
    assertEquals(CONTENT, emc.document().getContent(), "a failed load leaves the document alone");

    type("w");

    assertEquals("", View.of(emc).render().getString("message"), "any keypress, and the complaint is gone");
  }

  /** A paste is not a keypress, but it is just as much a thing the user did. */
  @Test
  void aPasteTakesTheComplaintAwayToo() {
    emc.session().reportError("saveFile: read-only file system");

    KeyWasPressedVerticle.pasteText(emc, "pasted");

    assertEquals("", View.of(emc).render().getString("message"));
  }
}
