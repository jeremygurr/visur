package com.ple.visur;

final class TextInsertion {
  private TextInsertion() {}

  static void insertAt(EditorModelCoupler emc, String value, int at) {
    String text = value.replace("\r\n", "\n");
    if (text.isEmpty()) return;
    String content = emc.document().getContent();
    int index = Math.max(0, Math.min(at, content.length()));
    emc.document().putContent(content.substring(0, index) + text + content.substring(index));
    int cursor = index + text.length() - 1;
    emc.session().putCA(cursor);
    int[] bounds = emc.session().getCursorQuantum().getBoundaries(cursor, emc.document().getNextLineIndices(), 0, false);
    emc.session().putCursorQuantumStartAndScroll(bounds[0]);
    emc.session().putCursorQuantumEndAndScroll(bounds[1]);
    emc.session().putIsAtQuantumStart(true);
  }
}
