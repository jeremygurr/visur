package com.ple.visur;

public class SearchBackwardSubmodeHandler implements KeymapHandler {
  public static SearchBackwardSubmodeHandler make() {
    return new SearchBackwardSubmodeHandler();
  }

  @Override
  public VisurCommand toVisurCommand(KeyPressed keyPressed) {
    EditorModelCoupler emc = ServiceHolder.editorModelCoupler;
    CommandCompileService ccs = CommandCompileService.make();
    String key = keyPressed.getKey();
    ExecutionDataStack eds = emc.session().getExecutionDataStack();
    if(key.length() == 1) {
      String searchTarget = "";
      if(eds.size() > 0) {
        searchTarget = (String) eds.pop();
      }
      searchTarget += key;
      eds.push(searchTarget); //add entry manually to not have to worry about weird regex edge case matching double quote or space within double quote
      return null;
    } else if(key.equals("Enter")) {
      String searchTarget = (String)eds.peek();
      emc.session().putPreviousSearchTarget(searchTarget);
      emc.session().putPreviousSearchDirectionWasForward(false);
      return ccs.compile("searchBackward \"navigate\" pushSubmode");
    } else if(key.equals("Escape")) {
      return ccs.compile("\"navigate\" pushSubmode");
    } else if(key.equals("Backspace")) {
      return ccs.compile("removeLastCharInSearchTarget");
    } else {
      return null;
    }
  }
}
