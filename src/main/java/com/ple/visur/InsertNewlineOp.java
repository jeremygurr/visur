package com.ple.visur;

public class InsertNewlineOp implements Operator {
  @Override
  public void execute(Object opInfo) {
    EditorModelCoupler emc = ServiceHolder.editorModelCoupler;
    String editorContent = emc.getEditorContent();
    int ca = emc.getCA();
    String contentBeforeChar = editorContent.substring(0, ca);
    String contentAfterChar = editorContent.substring(ca, editorContent.length());
    String resultingEditorContent = contentBeforeChar + "\n" + contentAfterChar;
    emc.putCA(++ca);
    emc.putEditorContent(resultingEditorContent);
    SessionText.record(emc, "\n");
    Quantum cursorQuantum = emc.getCursorQuantum();
    int[] bounds = cursorQuantum.getBoundaries(ca, emc.getNextLineIndices(), emc.getSpan(), false);
    emc.session().putCursorQuantumStartAndScroll(bounds[0]);
    emc.session().putCursorQuantumEndAndScroll(bounds[1]);
  }
}
