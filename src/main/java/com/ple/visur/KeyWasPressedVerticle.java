package com.ple.visur;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.rxjava3.core.eventbus.Message;

public class KeyWasPressedVerticle extends AbstractVisurVerticle {

  @Override
  public void start() {
    vertx.eventBus().consumer(BusEvent.keyWasPressed.name(), this::handle);
  }

  public void handle(Message event) {
    JsonObject keyJson = jsonBody(event);
    final String key = keyJson.getString("key");
    KeyPressed keyPressed = KeyPressed.from(key);
    EditorModelCoupler emc = ServiceHolder.editorModelCoupler;
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

}
