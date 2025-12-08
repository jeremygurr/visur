package com.ple.visur;

public class InsertModeHandler implements KeymapHandler {

  public static InsertModeHandler make() {
    return new InsertModeHandler();
  }

  @Override
  public VisurCommand toVisurCommand(KeyPressed keyPressed) {
    CommandCompileService ccs = CommandCompileService.make();
    String key = keyPressed.getKey();
    if(key.length() == 1) {
      return ccs.compile("\"" + key + "\" insertChar 1 0 relativeMove");
    } else {
      return null;
    }
  }

}
