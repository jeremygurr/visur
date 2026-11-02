package com.ple.visur;

public final class DocumentJumpOp {
  private DocumentJumpOp() {}

  public static void jumpToStart() {
    jumpTo(0);
  }

  public static void jumpToEnd() {
    jumpTo(bottomLineStart(ServiceHolder.editorModelCoupler.getEditorContent()));
  }

  static int bottomLineStart(String content) {
    int end = content.length();
    if (end > 0 && content.charAt(end - 1) == '\n') end--;
    return content.lastIndexOf('\n', end - 1) + 1;
  }

  private static void jumpTo(int index) {
    EditorModelCoupler emc = ServiceHolder.editorModelCoupler;
    emc.putCA(index);
    int[] bounds = emc.getCursorQuantum().getBoundaries(index, emc.getNextLineIndices(), 0, false);
    emc.putCursorQuantumStartAndScroll(bounds[0]);
    emc.putCursorQuantumEndAndScroll(bounds[1]);
    emc.putIsAtQuantumStart(true);
  }
}
