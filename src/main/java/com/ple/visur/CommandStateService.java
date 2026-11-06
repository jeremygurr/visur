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
    int commandCursor = ems.session().getCommandCursor();
    String commandStateKeyPressed = keyPressed.getKey();
    if(commandStateKeyPressed.equals("Escape")) {
      exitCommandState();
    } else if(commandStateKeyPressed.equals("Enter")) {
      String command = ems.session().getCommandStateContent();
      // The `:` grammar, not the sentence grammar: the command line is a command language, and a word in it that is
      // not a command says so rather than compiling to nothing.
      VisurCommand visurCommand = ServiceHolder.commandCompileService.compileCommandLine(command);
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
          ems.session().putCommandStateContent("");
          ems.session().putCommandCursor(0);
        } else {
          recall(ems, historyCursor);
        }
      }
    } else if(commandStateKeyPressed.equals("ArrowLeft") || commandStateKeyPressed.equals("ArrowRight")) {
      switch(commandStateKeyPressed) {
        case "ArrowLeft":
          if(commandCursor > 0) {
            commandCursor--;
            ems.session().putCommandCursor(commandCursor);
          }
          break;
        case "ArrowRight":
          if(commandCursor < ems.session().getCommandStateContent().length()) {
            commandCursor++;
            ems.session().putCommandCursor(commandCursor);
          }
          break;
        default:
          ems.session().reportError("unexpected error in switch statement for arrow movement in command state");
      }
    } else if(commandStateKeyPressed.equals("Backspace")) {
      String commandStateContent = ems.session().getCommandStateContent();
      if(commandCursor > 0) {
        String substrBeforeDeletedChar = commandStateContent.substring(0, commandCursor - 1);
        String substrAfterDeletedChar = commandStateContent.substring(commandCursor);
        String newCommandStateContent = substrBeforeDeletedChar + substrAfterDeletedChar;
        ems.session().putCommandStateContent(newCommandStateContent);
        ems.session().putCommandCursor(--commandCursor);
      }
    } else if(commandStateKeyPressed.length() == 1) {
      String oldCommandStateContent = ems.session().getCommandStateContent();
      char charToInsert = commandStateKeyPressed.charAt(0);
      String substrBeforeInsertedChar = oldCommandStateContent.substring(0, commandCursor);
      String substrAfterInsertedChar = oldCommandStateContent.substring(commandCursor);
      String newCommandStateContent = substrBeforeInsertedChar + charToInsert + substrAfterInsertedChar;

      ems.session().putCommandStateContent(newCommandStateContent);
      ems.session().putCommandCursor(commandCursor + 1);
    }
  }

  private void recall(EditorModelCoupler ems, int index) {
    String command = history.get(index);
    ems.session().putCommandStateContent(command);
    ems.session().putCommandCursor(command.length());
  }

  private void exitCommandState() {
    EditorModelCoupler ems = ServiceHolder.editorModelCoupler;
    ems.session().putIsInCommandState(false);
    ems.session().putCommandStateContent("");
    ems.session().putCommandCursor(0);
    System.out.println("Is out of command state");
  }

}
