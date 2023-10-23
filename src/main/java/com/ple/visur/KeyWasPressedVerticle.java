package com.ple.visur;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.rxjava3.core.eventbus.Message;

public class KeyWasPressedVerticle extends AbstractVisurVerticle {
  int lineStartY = 0;
  int lineStartX = 0;

  @Override
  public void start() {
    vertx.eventBus().consumer(BusEvent.keyWasPressed.name(), this::handle);
  }

  public void handle(Message event) {
    JsonObject keyJson = new JsonObject((String)event.body());
    final String key = keyJson.getString("key");
    mapKeys(key);
    boolean modelChanged = true;
    if(modelChanged) {
      bus.send(BusEvent.modelChange.name(), null);
    }
  }

  public Future<Void> keyPress(String key) {
    return Future.succeededFuture();
  }

  public void mapKeys(String key) {
    int x = editorModelService.getCursorX();
    int y = editorModelService.getCursorY();
    int currentLineNumber = editorModelService.getCurrentLineNumber();
    String currentLine = dataModelService.getContentLines()[currentLineNumber];
    int currentLineLength = currentLine.length();
    final int canvasWidth = editorModelService.getCanvasWidth();
    final int canvasHeight = editorModelService.getCanvasHeight();
    int lineEndY = lineStartY + currentLineLength / canvasWidth;
    if(currentLineLength % canvasWidth == 0) {
      lineEndY--;
    }
    int lineEndX = lineStartX + currentLineLength % canvasWidth - 1;
    if(lineEndX == -1) {
      lineEndX = canvasWidth - 1; //weird workaround
    }
    int interlinearX = editorModelService.getInterlinearX();

    System.out.println("line start x = " + lineStartX);
    System.out.println("line start y = " + lineStartY);
    System.out.println("line end x = " + lineEndX);
    System.out.println("line end y = " + lineEndY);
    System.out.println("canvas width = " + canvasWidth);

    if(key.equals("h")) {
      if(x > 0) {
        x--;
        editorModelService.putCursorX(x);
        editorModelService.putInterlinearX(x);
      }
    } else if (key.equals("j")) {
      String nextLine = currentLineNumber + 1 == dataModelService.getContentLines().length ?
        "" : dataModelService.getContentLines()[currentLineNumber + 1];
      int nextLineLength = nextLine.length();

      System.out.println("height = " + canvasHeight);
      System.out.println("y = " + y);
      if(y < canvasHeight - 1) {
        boolean endOfCurrentLine = y == lineEndY && currentLineNumber + 1 < dataModelService.getContentLines().length;
        boolean shouldGoDown;
        boolean shouldAdjustX;
        if(endOfCurrentLine) {
          shouldGoDown = currentLineNumber + 1 < dataModelService.getContentLines().length;
          shouldAdjustX = nextLineLength != x + 1 && shouldGoDown;
        } else {
          shouldGoDown = !(y + 1 > lineEndY || currentLineLength == canvasWidth * (y + 1));
          shouldAdjustX = shouldGoDown && x != lineEndX && y == lineEndY - 1;
        }
        if(shouldGoDown) {
          y++;
          if(y > lineEndY) {
            editorModelService.putCurrentLineNumber(currentLineNumber + 1);
            lineStartY = y;
          }
          if(shouldAdjustX) {
            if(y == lineEndY) {
              x = lineEndX;
            } else {
              x = interlinearX;
            }
          }
        }
      }
      System.out.println("y = " + y);
      editorModelService.putCursorY(y);
      System.out.println("current line number = " + editorModelService.getCurrentLineNumber());
      editorModelService.putCursorX(x);
      System.out.println("y = " + editorModelService.getCursorY());
    } else if (key.equals("k")) {
      String previousLine = currentLineNumber - 1 < 0 ?
        "" : dataModelService.getContentLines()[currentLineNumber - 1];
      int previousLineLength = previousLine.length();

      System.out.println("height = " + canvasHeight);
      System.out.println("y = " + y);
      if(y > 0) {
        boolean beginningOfCurrentLine = y == lineStartY;
        boolean shouldGoUp;
        boolean shouldAdjustX;
        if(beginningOfCurrentLine) {
          shouldGoUp = currentLineNumber - 1 >= 0;
          shouldAdjustX = previousLineLength % canvasWidth < x + 1 && shouldGoUp;
        } else {
          shouldGoUp = true;
          shouldAdjustX = false;
        }
        if(shouldGoUp) {
          y--;
          if(y < lineStartY) {
            editorModelService.putCurrentLineNumber(currentLineNumber - 1);
            lineEndY = y;
            updateLineStartY(lineEndY);
          }
          if(shouldAdjustX) {
            x = previousLineLength % canvasWidth - 1;
          }
        }
      }
      System.out.println("y = " + y);
      editorModelService.putCursorY(y);
      System.out.println("current line number = " + editorModelService.getCurrentLineNumber());
      editorModelService.putCursorX(x);
      System.out.println("y = " + editorModelService.getCursorY());
    } else if (key.equals("l")) {
      final Integer width = editorModelService.getCanvasWidth();
      if(x < width - 1) {
        boolean shouldGoRight = !(editorModelService.getCursorX() == lineEndX &&
          editorModelService.getCursorY() >= lineEndY);
        if(shouldGoRight) {
          x++;
          editorModelService.putCursorX(x);
          editorModelService.putInterlinearX(x);
        }
      }
    }
  }

  private void updateLineStartY(int lineEndY) {
    String currentContentLine = dataModelService.getContentLines()[editorModelService.getCurrentLineNumber()];
    int currentContentLineLength = currentContentLine.length();
    int canvasWidth = editorModelService.getCanvasWidth();
    lineStartY = lineEndY - currentContentLineLength / canvasWidth;
    if(currentContentLineLength % canvasWidth == 0) {
      lineStartY++;
    }
  }

}
