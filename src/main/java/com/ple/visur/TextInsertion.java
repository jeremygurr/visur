package com.ple.visur;

final class TextInsertion {
  private TextInsertion() {}

  static void insertAt(EditorModelCoupler emc, String value, int at) {
    String text = value.replace("\r\n", "\n");
    if (text.isEmpty()) return;
    String content = emc.getEditorContent();
    int index = Math.max(0, Math.min(at, content.length()));
    emc.putEditorContent(content.substring(0, index) + text + content.substring(index));
    int cursor = index + text.length() - 1;
    emc.putCA(cursor);
    int[] bounds = emc.getCursorQuantum().getBoundaries(cursor, emc.getNextLineIndices(), 0, false);
    emc.putCursorQuantumStartAndScroll(bounds[0]);
    emc.putCursorQuantumEndAndScroll(bounds[1]);
    emc.putIsAtQuantumStart(true);
  }
}
