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
   * line too, so a command typed from the keyboard began with a prompt character nothing could compile. Every test in
   * this file called the two halves separately and never saw it.
   *
   * <p>It presses the key rather than calling {@code enterCommandStateIfRequested}, because the bug lived in the
   * handoff between those two halves, not in either one.
   */
  @Test
  void theKeyThatOpensCommandStateIsNotPartOfTheCommand() {
    ServiceHolder.editorModelCoupler.session().putIsInCommandState(false);
    ServiceHolder.editorModelCoupler.session().putCommandStateContent("stale");

    KeyWasPressedVerticle.press(ServiceHolder.editorModelCoupler, KeyPressed.from(":"), ":");

    assertTrue(ServiceHolder.editorModelCoupler.session().getIsInCommandState());
    assertEquals("", ServiceHolder.editorModelCoupler.session().getCommandStateContent(), "the prompt is drawn, not typed");
  }

  /**
   * `;` is gone rather than kept alongside: two keys for one door is two keys to draw, document and test forever. What
   * it has to do is <em>nothing</em> — not open command state, and not fall through as a keystroke either, since a
   * user with the old key in their fingers typing {@code ;w} should get the absence of a command rather than a `w` in
   * whatever mode they were already standing in.
   */
  @Test
  void theOldCommandKeyOpensNothingAndDoesNothing() {
    ServiceHolder.editorModelCoupler.session().putIsInCommandState(false);
    ServiceHolder.editorModelCoupler.session().putCommandStateContent("");

    KeyWasPressedVerticle.press(ServiceHolder.editorModelCoupler, KeyPressed.from(";"), ";");

    assertFalse(ServiceHolder.editorModelCoupler.session().getIsInCommandState(), "the old key is not a second door");
    assertEquals("", ServiceHolder.editorModelCoupler.session().getCommandStateContent());
    assertEquals(EditorSubmode.navigate, ServiceHolder.editorModelCoupler.session().getEditorSubmode());
  }

  /**
   * The history outlives the rename because it stores commands rather than keystrokes: {@code ArrowUp} hands back what
   * was executed, and the `:` that opened the line was never part of it.
   */
  @Test
  void historyRecallsACommandTypedWithTheNewKey() {
    // The fixture has command state open with a sentence in it, because most of this file tests the line editor
    // directly. This test wants a keyboard, so it starts where a keyboard starts: navigate mode, nothing typed.
    ServiceHolder.editorModelCoupler.session().putIsInCommandState(false);
    ServiceHolder.editorModelCoupler.session().putCommandStateContent("");
    KeyWasPressedVerticle.press(ServiceHolder.editorModelCoupler, KeyPressed.from(":"), ":");
    for (String key : new String[]{"c", "l", "e", "a", "r", "E", "D", "S"}) {
      ServiceHolder.commandStateService.executeCommandState(KeyPressed.from(key));
    }
    ServiceHolder.commandStateService.executeCommandState(KeyPressed.from("Enter"));

    KeyWasPressedVerticle.press(ServiceHolder.editorModelCoupler, KeyPressed.from(":"), ":");
    ServiceHolder.commandStateService.executeCommandState(KeyPressed.from("ArrowUp"));

    assertEquals("clearEDS", ServiceHolder.editorModelCoupler.session().getCommandStateContent());
    assertEquals(8, ServiceHolder.editorModelCoupler.session().getCommandCursor(), "recalled whole, cursor past the end");
  }

  @Test
  void colonEntersOnlyFromNavigateSubmode() {
    ServiceHolder.editorModelCoupler.session().putCommandStateContent("stale");
    ServiceHolder.editorModelCoupler.session().putCommandCursor(5);
    ServiceHolder.editorModelCoupler.session().putIsInCommandState(false);
    KeyWasPressedVerticle.enterCommandStateIfRequested(ServiceHolder.editorModelCoupler, ":");
    assertEquals(true, ServiceHolder.editorModelCoupler.session().getIsInCommandState());
    assertEquals("", ServiceHolder.editorModelCoupler.session().getCommandStateContent());

    ServiceHolder.editorModelCoupler.session().putIsInCommandState(false);
    java.util.Stack<EditorSubmode> submodes = new java.util.Stack<>();
    submodes.push(EditorSubmode.insert);
    ServiceHolder.editorModelCoupler.session().putEditorSubmodeStack(submodes);
    KeyWasPressedVerticle.enterCommandStateIfRequested(ServiceHolder.editorModelCoupler, ":");
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

  /**
   * Enter is the run key, and leaving command state is part of running. What it runs is a `:` command now rather than a
   * raw editor sentence — the door and the grammar changed together — so this asserts the mechanics of Enter (it ran, it
   * said something on screen, the line closed) and leaves the grammar to {@link CommandLineTest}, which types the keys.
   */
  @Test
  void enterCompilesAndExecutesCommand() {
    ServiceHolder.editorModelCoupler.session().putCommandStateContent("frobnicate");
    ServiceHolder.editorModelCoupler.session().putCommandCursor("frobnicate".length());
    ServiceHolder.editorModelCoupler.session().getExecutionDataStack().clear();

    ServiceHolder.commandStateService.executeCommandState(KeyPressed.from("Enter"));

    assertTrue(ServiceHolder.editorModelCoupler.session().getMessage().contains("frobnicate"),
      "the command ran far enough to say what it was: " + ServiceHolder.editorModelCoupler.session().getMessage());
    assertEquals(0, ServiceHolder.editorModelCoupler.session().getExecutionDataStack().size(),
      "and a command nothing matched pushed no arguments onto the stack");
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
