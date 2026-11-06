package com.ple.visur;

public class SearchForwardSubmodeHandler implements KeymapHandler {
  public static SearchForwardSubmodeHandler make() {
    return new SearchForwardSubmodeHandler();
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
      String searchTarget = "";
      if(eds.size() > 0) {
        searchTarget = (String) eds.peek();
      }
      emc.session().putPreviousSearchTarget(searchTarget);
      emc.session().putPreviousSearchDirectionWasForward(true);
      return ccs.compile("searchForward \"navigate\" pushSubmode");
    } else if(key.equals("Escape")) {
      return ccs.compile("\"navigate\" pushSubmode");
    } else if(key.equals("Backspace")) {
      return ccs.compile("removeLastCharInSearchTarget");
    } else {
      return null;
    }
  }
}
