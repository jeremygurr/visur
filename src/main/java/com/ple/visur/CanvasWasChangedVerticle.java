package com.ple.visur;

import io.vertx.core.json.JsonObject;
import io.vertx.rxjava3.core.eventbus.Message;

import java.util.ArrayList;

public class CanvasWasChangedVerticle extends AbstractVisurVerticle {

  @Override
  public void start() {
    vertx.eventBus().consumer(BusEvent.canvasWasChanged.name(), this::handle);
  }

  public void handle(Message event) {
    JsonObject canvasJson = new JsonObject((String)event.body());
    final Integer width = canvasJson.getInteger("width");
    final EditorModelCoupler emc = ServiceHolder.editorModelCoupler;
    emc.putCanvasWidth(width);
    final Integer height = canvasJson.getInteger("height");
    emc.putCanvasHeight(height);
    ArrayList<Integer> nextLineIndices = emc.getNextLineIndices();
    int cy = emc.getCY();
    //simply a line by line log of editorContent
    for(int i = 0; i <= nextLineIndices.size(); i++) {
      String editorContent = emc.getEditorContent();
      int lineStart = cy > 0 ? nextLineIndices.get(cy - 1) : 0;
      int lineEnd = Math.max(nextLineIndices.get(cy) - 1, 0);
      String line = editorContent.substring(lineStart, lineEnd);
      if(line != null) {
        System.out.println("Line " + i + ": " + line);
      }
    }
    vertx.eventBus().send(BusEvent.modelWasChanged.name(), "true");
  }

}
