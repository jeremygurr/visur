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
    if(CountPrefix.record(emc, key)) {
      return; // a digit in navigate mode is the count for the next key, not a command of its own
    }
    enterCommandStateIfRequested(emc, key);
    if(emc.getIsInCommandState()) {
      ServiceHolder.commandStateService.executeCommandState(keyPressed);
    } else {
      KeymapMap keymapMap = emc.getKeymapMap();
      Keymap keymap = keymapMap.get(emc.getEditorSubmode());
      VisurCommand currentCommand = keymap.get(keyPressed);
      CommandExecutionService ces = ServiceHolder.commandExecutionService.make();
      if(currentCommand != null) {
        ces.execute(currentCommand);
      }
    }
  }

  private void handlePaste(Message event) {
    EditorModelCoupler emc = ServiceHolder.editorModelCoupler;
    emc.clearPendingCount(); // typed text arrives without a keypress, so it retires a pending count the way one would
    JsonObject body = jsonBody(event);
    String text = body.getString("text", "");
    if (!emc.getIsInCommandState() && emc.getEditorSubmode() == EditorSubmode.navigate) {
      TextInsertion.insertAt(emc, text, emc.getCA());
    }
    bus.send(BusEvent.modelWasChanged.name(), null);
  }

  static void enterCommandStateIfRequested(EditorModelCoupler emc, String key) {
    if(!emc.getIsInCommandState() && key.equals(";") && emc.getEditorSubmode() == EditorSubmode.navigate) {
      emc.putIsInCommandState(true);
      emc.putCommandStateContent("");
      emc.putCommandCursor(0);
    }
  }

}
