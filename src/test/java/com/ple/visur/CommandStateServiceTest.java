package com.ple.visur;

import io.vertx.rxjava3.core.Vertx;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

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
    ServiceHolder.editorModelCoupler.putEditorSubmodeStack(submodes);
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
    assertEquals(1, ServiceHolder.editorModelCoupler.getExecutionDataStack().peek());
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
