package com.ple.visur;

import java.util.ArrayList;
import java.util.List;

public class CommandStateService {

  private static final int HISTORY_LIMIT = 20;
  private final List<String> history = new ArrayList<>();
  private int historyCursor;

  public static CommandStateService make() {
    return new CommandStateService();
  }

  public void executeCommandState(KeyPressed keyPressed) {
    EditorModelCoupler ems = ServiceHolder.editorModelCoupler;
    int commandCursor = ems.getCommandCursor();
    String commandStateKeyPressed = keyPressed.getKey();
    if(commandStateKeyPressed.equals("Escape")) {
      exitCommandState();
    } else if(commandStateKeyPressed.equals("Enter")) {
      String command = ems.getCommandStateContent();
      VisurCommand visurCommand = ServiceHolder.commandCompileService.compile(command);
      ServiceHolder.commandExecutionService.execute(visurCommand);
      if(!command.isEmpty()) {
        history.add(command);
        if(history.size() > HISTORY_LIMIT) {
          history.remove(0);
        }
      }
      historyCursor = history.size();
      exitCommandState();
    } else if(commandStateKeyPressed.equals("ArrowUp")) {
      if(historyCursor > 0) {
        recall(ems, --historyCursor);
      }
    } else if(commandStateKeyPressed.equals("ArrowDown")) {
      if(historyCursor < history.size()) {
        historyCursor++;
        if(historyCursor == history.size()) {
          ems.putCommandStateContent("");
          ems.putCommandCursor(0);
        } else {
          recall(ems, historyCursor);
        }
      }
    } else if(commandStateKeyPressed.equals("ArrowLeft") || commandStateKeyPressed.equals("ArrowRight")) {
      switch(commandStateKeyPressed) {
        case "ArrowLeft":
          if(commandCursor > 0) {
            commandCursor--;
            ems.putCommandCursor(commandCursor);
          }
          break;
        case "ArrowRight":
          if(commandCursor < ems.getCommandStateContent().length()) {
            commandCursor++;
            ems.putCommandCursor(commandCursor);
          }
          break;
        default:
          ems.reportError("unexpected error in switch statement for arrow movement in command state");
      }
    } else if(commandStateKeyPressed.equals("Backspace")) {
      String commandStateContent = ems.getCommandStateContent();
      if(commandCursor > 0) {
        String substrBeforeDeletedChar = commandStateContent.substring(0, commandCursor - 1);
        String substrAfterDeletedChar = commandStateContent.substring(commandCursor);
        String newCommandStateContent = substrBeforeDeletedChar + substrAfterDeletedChar;
        ems.putCommandStateContent(newCommandStateContent);
        ems.putCommandCursor(--commandCursor);
      }
    } else if(commandStateKeyPressed.length() == 1) {
      String oldCommandStateContent = ems.getCommandStateContent();
      char charToInsert = commandStateKeyPressed.charAt(0);
      String substrBeforeInsertedChar = oldCommandStateContent.substring(0, commandCursor);
      String substrAfterInsertedChar = oldCommandStateContent.substring(commandCursor);
      String newCommandStateContent = substrBeforeInsertedChar + charToInsert + substrAfterInsertedChar;

      ems.putCommandStateContent(newCommandStateContent);
      ems.putCommandCursor(commandCursor + 1);
    }
  }

  private void recall(EditorModelCoupler ems, int index) {
    String command = history.get(index);
    ems.putCommandStateContent(command);
    ems.putCommandCursor(command.length());
  }

  private void exitCommandState() {
    EditorModelCoupler ems = ServiceHolder.editorModelCoupler;
    ems.putIsInCommandState(false);
    ems.putCommandStateContent("");
    ems.putCommandCursor(0);
    System.out.println("Is out of command state");
  }

}
