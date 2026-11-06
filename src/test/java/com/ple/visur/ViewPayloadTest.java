package com.ple.visur;

import io.vertx.rxjava3.core.Vertx;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.ple.visur.EditorSubmode.navigate;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * What a renderer is handed to draw the mode line: the quantum, the fence, the span, and the count in flight.
 *
 * <p>The tail of that line is the whole contract, so it is asserted on the serialised payload rather than on the
 * record — a field that exists in Java and not on the wire is a mode line that silently reads zero.
 */
class ViewPayloadTest {
  private static final String CONTENT = "one two three four five\n";

  private Vertx vertx;
  private EditorModelCoupler emc;

  @BeforeEach
  void setUp() {
    vertx = Vertx.vertx();
    ServiceHolder.init(vertx.sharedData());
    ServiceHolder.initializerService.initializeEditorModel();
    emc = ServiceHolder.editorModelCoupler;
    emc.setSourceFilePath("");
    emc.putEditorContent(CONTENT);
    emc.putCanvasWidth(80);
    emc.putCanvasHeight(24);
    emc.putCanvasStart(0);
    emc.putCA(0);
    java.util.Stack<EditorSubmode> submodes = new java.util.Stack<>();
    submodes.push(navigate);
    emc.putEditorSubmodeStack(submodes);
    emc.putIsInCommandState(false);
    emc.putCursorQuantum(emc.getQuantumNameToQuantum().get("word"));
    emc.putScopeQuantum(emc.getQuantumNameToQuantum().get("line"));
    emc.putSpan(3);
  }

  @AfterEach
  void tearDown() {
    vertx.close();
  }

  @Test
  void thePayloadCarriesTheFenceAndTheSpanTheModeLineShows() {
    var body = View.of(emc).render();
    assertEquals("word", body.getString("cursorQuantum"));
    assertEquals("line", body.getString("scopeQuantum"));
    assertEquals(3, body.getInteger("span"));
  }

  /** A digit with no feedback on screen is indistinguishable from a dropped key, so a pending count travels. */
  @Test
  void thePayloadCarriesACountThatHasNotBeenSpentYet() {
    assertEquals(0, View.of(emc).render().getInteger("pendingCount"), "no count typed is no count shown");

    KeyWasPressedVerticle.press(emc, KeyPressed.from("3"), "3");
    assertEquals(3, View.of(emc).render().getInteger("pendingCount"));

    KeyWasPressedVerticle.press(emc, KeyPressed.from("1"), "1");
    KeyWasPressedVerticle.press(emc, KeyPressed.from("2"), "2");
    assertEquals(312, View.of(emc).render().getInteger("pendingCount"), "digits accumulate into one count");
  }

  /**
   * An error the user cannot see is an error that never happened: a failed load and an unbound key look identical on
   * screen. So the message travels to the renderer like any other mode-line field, and an editor that has reported
   * nothing shows nothing.
   */
  @Test
  void aReportedErrorTravelsToThePayloadAndAnEditorThatReportedNothingShowsNothing() {
    assertEquals("", View.of(emc).render().getString("message"), "nothing reported is nothing shown");

    emc.reportError("loadFile: file not found: /tmp/gone.txt");

    assertEquals("loadFile: file not found: /tmp/gone.txt", View.of(emc).render().getString("message"));
  }
}
