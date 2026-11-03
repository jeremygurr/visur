package com.ple.visur;

public enum EditorSubmode {
  navigate,
  insert,
  quantumStart,
  quantumEnd,
  replace,
  searchForward,
  searchBackward,
  changeScope;

  public static EditorSubmode getSubmodeByString(String str) {
    for(EditorSubmode submode : EditorSubmode.values()) {
      if(submode.name().equals(str)) {
        return submode;
      }
    }
    return null;
  }
}
