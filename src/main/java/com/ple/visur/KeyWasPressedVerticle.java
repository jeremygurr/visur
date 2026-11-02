package com.ple.visur;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.rxjava3.core.eventbus.Message;

public class KeyWasPressedVerticle extends AbstractVisurVerticle {

  private boolean pendingDocumentStart;

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
    if (emc.getIsInCommandState()) {
      pendingDocumentStart = false;
    } else {
      boolean navigating = emc.getEditorSubmode() == EditorSubmode.navigate;
      if (pendingDocumentStart) {
        pendingDocumentStart = false;
        if (navigating && key.equals("g")) {
          DocumentJumpOp.jumpToStart();
          bus.send(BusEvent.modelWasChanged.name(), null);
          return;
        }
      }
      if (navigating && key.equals("g")) {
        pendingDocumentStart = true;
        bus.send(BusEvent.modelWasChanged.name(), null);
        return;
      }
      if (navigating && key.equals("G")) {
        DocumentJumpOp.jumpToEnd();
        bus.send(BusEvent.modelWasChanged.name(), null);
        return;
      }
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

    bus.send(BusEvent.modelWasChanged.name(), null);

  }

  private void handlePaste(Message event) {
    pendingDocumentStart = false;
    EditorModelCoupler emc = ServiceHolder.editorModelCoupler;
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
