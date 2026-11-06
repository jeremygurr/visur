package com.ple.visur;

public class EditingModeHandler implements KeymapHandler {

  final EditorModelCoupler emc = ServiceHolder.editorModelCoupler;

  public static EditingModeHandler make() {
    return new EditingModeHandler();
  }

  @Override
  public VisurCommand toVisurCommand(KeyPressed keyPressed) {
    final KeymapMap keymapMap = emc.getKeymapMap();
    final EditorSubmode submode = emc.session().getEditorSubmode();
    final Keymap keymap = keymapMap.get(submode);
    VisurCommand visurCommand = keymap.get(keyPressed);
    return visurCommand;
  }

}
