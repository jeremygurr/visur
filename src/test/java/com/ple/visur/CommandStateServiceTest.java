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
    ServiceHolder.init(vertx.sharedData());
    ServiceHolder.editorModelCoupler.putIsInCommandState(true);
    ServiceHolder.editorModelCoupler.putCommandStateContent("clearEDS");
    ServiceHolder.editorModelCoupler.putCommandCursor("clearEDS".length());
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
    ServiceHolder.editorModelCoupler.putIsInCommandState(false);
    ServiceHolder.editorModelCoupler.putCommandStateContent("stale");

    KeyWasPressedVerticle.press(ServiceHolder.editorModelCoupler, KeyPressed.from(";"), ";");

    assertTrue(ServiceHolder.editorModelCoupler.getIsInCommandState());
    assertEquals("", ServiceHolder.editorModelCoupler.getCommandStateContent(), "the prompt is drawn, not typed");
  }

  @Test
  void semicolonEntersOnlyFromNavigateSubmode() {
    ServiceHolder.editorModelCoupler.putCommandStateContent("stale");
    ServiceHolder.editorModelCoupler.putCommandCursor(5);
    ServiceHolder.editorModelCoupler.putIsInCommandState(false);
    KeyWasPressedVerticle.enterCommandStateIfRequested(ServiceHolder.editorModelCoupler, ";");
    assertEquals(true, ServiceHolder.editorModelCoupler.getIsInCommandState());
    assertEquals("", ServiceHolder.editorModelCoupler.getCommandStateContent());

    ServiceHolder.editorModelCoupler.putIsInCommandState(false);
    java.util.Stack<EditorSubmode> submodes = new java.util.Stack<>();
    submodes.push(EditorSubmode.insert);
    ServiceHolder.editorModelCoupler.session().putEditorSubmodeStack(submodes);
    KeyWasPressedVerticle.enterCommandStateIfRequested(ServiceHolder.editorModelCoupler, ";");
    assertFalse(ServiceHolder.editorModelCoupler.getIsInCommandState());
  }

  @Test
  void escapeExitsAndClearsTheCommandLine() {
    ServiceHolder.commandStateService.executeCommandState(KeyPressed.from("Escape"));
    assertFalse(ServiceHolder.editorModelCoupler.getIsInCommandState());
    assertEquals("", ServiceHolder.editorModelCoupler.getCommandStateContent());
  }

  @Test
  void editsAtTheCursorAndBackspaceThenEscapeClears() {
    ServiceHolder.commandStateService.executeCommandState(KeyPressed.from("ArrowLeft"));
    ServiceHolder.commandStateService.executeCommandState(KeyPressed.from("x"));
    assertEquals("clearEDxS", ServiceHolder.editorModelCoupler.getCommandStateContent());
    assertEquals(8, ServiceHolder.editorModelCoupler.getCommandCursor());
    ServiceHolder.commandStateService.executeCommandState(KeyPressed.from("Backspace"));
    assertEquals("clearEDS", ServiceHolder.editorModelCoupler.getCommandStateContent());
    assertEquals(7, ServiceHolder.editorModelCoupler.getCommandCursor());
    ServiceHolder.commandStateService.executeCommandState(KeyPressed.from("Escape"));
    assertEquals("", ServiceHolder.editorModelCoupler.getCommandStateContent());
  }

  @Test
  void enterCompilesAndExecutesCommand() {
    ServiceHolder.editorModelCoupler.putCommandStateContent("1");
    ServiceHolder.editorModelCoupler.putCommandCursor(1);
    ServiceHolder.commandStateService.executeCommandState(KeyPressed.from("Enter"));
    assertEquals(1, ServiceHolder.editorModelCoupler.session().getExecutionDataStack().peek());
    assertFalse(ServiceHolder.editorModelCoupler.getIsInCommandState());
  }

  @Test
  void recallsExecutedCommandAndMovesCursorToEnd() {
    ServiceHolder.commandStateService.executeCommandState(KeyPressed.from("Enter"));
    assertFalse(ServiceHolder.editorModelCoupler.getIsInCommandState());

    ServiceHolder.editorModelCoupler.putIsInCommandState(true);
    ServiceHolder.commandStateService.executeCommandState(KeyPressed.from("ArrowUp"));

    assertEquals("clearEDS", ServiceHolder.editorModelCoupler.getCommandStateContent());
    assertEquals("clearEDS".length(), ServiceHolder.editorModelCoupler.getCommandCursor());

    ServiceHolder.commandStateService.executeCommandState(KeyPressed.from("ArrowDown"));
    assertEquals("", ServiceHolder.editorModelCoupler.getCommandStateContent());
    assertEquals(0, ServiceHolder.editorModelCoupler.getCommandCursor());
  }
}
