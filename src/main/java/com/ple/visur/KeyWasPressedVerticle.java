package com.ple.visur;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.rxjava3.core.eventbus.Message;

public class KeyWasPressedVerticle extends AbstractVisurVerticle {

  @Override
  public void start() {
    vertx.eventBus().consumer(BusEvent.keyWasPressed.name(), this::handle);
    vertx.eventBus().consumer(BusEvent.pasteWasReceived.name(), this::handlePaste);
  }

  public void handle(Message event) {
    JsonObject keyJson = jsonBody(event);
    final String key = keyJson.getString("key");
    KeyPressed keyPressed = KeyPressed.from(key);
    EditorModelCoupler emc = ServiceHolder.editorModelCoupler;
    // Before the key runs: wrapping, the scroll window and the cursor column are all the asking client's geometry,
    // and an operator forty calls down reads them off the model rather than being handed them.
    emc.putConnection(Connections.headerOf(event));
    try {
      press(emc, keyPressed, key);
    } finally {
      // every keypress but a digit ends whatever count was pending, whether or not it matched a binding
      CountPrefix.retireUnlessDigit(emc, key);
    }
    bus.send(BusEvent.modelWasChanged.name(), null);
  }

  /**
   * One keypress: the count typed in front of it, then the current submode's keymap, then the command it compiles to.
   *
   * <p>Static and side-effect-free of the bus so a test can drive a key without a verticle context, which is what
   * {@link CountPrefixTest} needs to show {@code 12 s} reaching the model.
   */
  static void press(EditorModelCoupler emc, KeyPressed keyPressed, String key) {
    // Before the key runs, not after: a message reports what the last thing the user asked for did, and the key they
    // are pressing now is what makes it stale. Clearing afterwards would erase the message this very key reports.
    emc.clearMessage();
    if(CountPrefix.record(emc, key)) {
      return; // a digit in navigate mode is the count for the next key, not a command of its own
    }
    if(enterCommandStateIfRequested(emc, key)) {
      // The prompt is drawn by the renderers, and a command line that began with the `;` that opened it compiled to
      // nothing, so this key opens command state and does nothing else.
      return;
    }
    if(emc.getIsInCommandState()) {
      ServiceHolder.commandStateService.executeCommandState(keyPressed);
    } else {
      KeymapMap keymapMap = emc.getKeymapMap();
      Keymap keymap = keymapMap.get(emc.getEditorSubmode());
      VisurCommand currentCommand = keymap.get(keyPressed);
      CommandExecutionService ces = ServiceHolder.commandExecutionService.make();
      if(currentCommand != null) {
        // The one place a command runs, so this is the one place a command can be snapshotted before it changes
        // content. UndoHistory decides whether what it changes is worth a unit.
        UndoHistory history = emc.getUndoHistory();
        history.beforeCommand(emc);
        ces.execute(currentCommand);
        history.afterCommand(emc);
      }
    }
  }

  private void handlePaste(Message event) {
    JsonObject body = jsonBody(event);
    EditorModelCoupler emc = ServiceHolder.editorModelCoupler;
    emc.putConnection(Connections.headerOf(event)); // a paste wraps text at the width the client asked to be wrapped at
    pasteText(emc, body.getString("text", ""));
    bus.send(BusEvent.modelWasChanged.name(), null);
  }

  /** A pasted block arrives on the bus rather than as a key; static for the same reason {@link #press} is. */
  static void pasteText(EditorModelCoupler emc, String text) {
    emc.clearPendingCount(); // typed text arrives without a keypress, so it retires a pending count the way one would
    emc.clearMessage(); // and it is a thing the user did, which is what retires a message
    if (!emc.getIsInCommandState() && emc.getEditorSubmode() == EditorSubmode.navigate) {
      UndoHistory history = emc.getUndoHistory();
      history.beforeCommand(emc);
      TextInsertion.insertAt(emc, text, emc.getCA());
      history.afterCommand(emc);
    }
  }

  /** @return whether this keypress opened command state, so the caller knows the key is spent */
  static boolean enterCommandStateIfRequested(EditorModelCoupler emc, String key) {
    if(!emc.getIsInCommandState() && key.equals(";") && emc.getEditorSubmode() == EditorSubmode.navigate) {
      emc.putIsInCommandState(true);
      emc.putCommandStateContent("");
      emc.putCommandCursor(0);
      return true;
    }
    return false;
  }

}
