package com.ple.visur;

import io.vertx.rxjava3.core.Vertx;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommandStateServiceTest {
  private Vertx vertx;

  @BeforeEach
  void setUp() {
    vertx = Vertx.vertx();
    ServiceHolder.init();
    ServiceHolder.editorModelCoupler.session().putIsInCommandState(true);
    ServiceHolder.editorModelCoupler.session().putCommandStateContent("clearEDS");
    ServiceHolder.editorModelCoupler.session().putCommandCursor("clearEDS".length());
  }

  @AfterEach
  void tearDown() {
    vertx.close();
  }

  /**
   * The `;` is the door, not the first word of the sentence. Entering command state left the content empty and the
   * renderers draw the prompt themselves — but the keypress that opened command state fell through into the command
   * line too, so a command typed from the keyboard began with a `;` nothing could compile. Every test in this file
   * called the two halves separately and never saw it.
   */
  @Test
  void theKeyThatOpensCommandStateIsNotPartOfTheCommand() {
    ServiceHolder.editorModelCoupler.session().putIsInCommandState(false);
    ServiceHolder.editorModelCoupler.session().putCommandStateContent("stale");

    KeyWasPressedVerticle.press(ServiceHolder.editorModelCoupler, KeyPressed.from(";"), ";");

    assertTrue(ServiceHolder.editorModelCoupler.session().getIsInCommandState());
    assertEquals("", ServiceHolder.editorModelCoupler.session().getCommandStateContent(), "the prompt is drawn, not typed");
  }

  @Test
  void semicolonEntersOnlyFromNavigateSubmode() {
    ServiceHolder.editorModelCoupler.session().putCommandStateContent("stale");
    ServiceHolder.editorModelCoupler.session().putCommandCursor(5);
    ServiceHolder.editorModelCoupler.session().putIsInCommandState(false);
    KeyWasPressedVerticle.enterCommandStateIfRequested(ServiceHolder.editorModelCoupler, ";");
    assertEquals(true, ServiceHolder.editorModelCoupler.session().getIsInCommandState());
    assertEquals("", ServiceHolder.editorModelCoupler.session().getCommandStateContent());

    ServiceHolder.editorModelCoupler.session().putIsInCommandState(false);
    java.util.Stack<EditorSubmode> submodes = new java.util.Stack<>();
    submodes.push(EditorSubmode.insert);
    ServiceHolder.editorModelCoupler.session().putEditorSubmodeStack(submodes);
    KeyWasPressedVerticle.enterCommandStateIfRequested(ServiceHolder.editorModelCoupler, ";");
    assertFalse(ServiceHolder.editorModelCoupler.session().getIsInCommandState());
  }

  @Test
  void escapeExitsAndClearsTheCommandLine() {
    ServiceHolder.commandStateService.executeCommandState(KeyPressed.from("Escape"));
    assertFalse(ServiceHolder.editorModelCoupler.session().getIsInCommandState());
    assertEquals("", ServiceHolder.editorModelCoupler.session().getCommandStateContent());
  }

  @Test
  void editsAtTheCursorAndBackspaceThenEscapeClears() {
    ServiceHolder.commandStateService.executeCommandState(KeyPressed.from("ArrowLeft"));
    ServiceHolder.commandStateService.executeCommandState(KeyPressed.from("x"));
    assertEquals("clearEDxS", ServiceHolder.editorModelCoupler.session().getCommandStateContent());
    assertEquals(8, ServiceHolder.editorModelCoupler.session().getCommandCursor());
    ServiceHolder.commandStateService.executeCommandState(KeyPressed.from("Backspace"));
    assertEquals("clearEDS", ServiceHolder.editorModelCoupler.session().getCommandStateContent());
    assertEquals(7, ServiceHolder.editorModelCoupler.session().getCommandCursor());
    ServiceHolder.commandStateService.executeCommandState(KeyPressed.from("Escape"));
    assertEquals("", ServiceHolder.editorModelCoupler.session().getCommandStateContent());
  }

  @Test
  void enterCompilesAndExecutesCommand() {
    ServiceHolder.editorModelCoupler.session().putCommandStateContent("1");
    ServiceHolder.editorModelCoupler.session().putCommandCursor(1);
    ServiceHolder.commandStateService.executeCommandState(KeyPressed.from("Enter"));
    assertEquals(1, ServiceHolder.editorModelCoupler.session().getExecutionDataStack().peek());
    assertFalse(ServiceHolder.editorModelCoupler.session().getIsInCommandState());
  }

  @Test
  void recallsExecutedCommandAndMovesCursorToEnd() {
    ServiceHolder.commandStateService.executeCommandState(KeyPressed.from("Enter"));
    assertFalse(ServiceHolder.editorModelCoupler.session().getIsInCommandState());

    ServiceHolder.editorModelCoupler.session().putIsInCommandState(true);
    ServiceHolder.commandStateService.executeCommandState(KeyPressed.from("ArrowUp"));

    assertEquals("clearEDS", ServiceHolder.editorModelCoupler.session().getCommandStateContent());
    assertEquals("clearEDS".length(), ServiceHolder.editorModelCoupler.session().getCommandCursor());

    ServiceHolder.commandStateService.executeCommandState(KeyPressed.from("ArrowDown"));
    assertEquals("", ServiceHolder.editorModelCoupler.session().getCommandStateContent());
    assertEquals(0, ServiceHolder.editorModelCoupler.session().getCommandCursor());
  }
}
