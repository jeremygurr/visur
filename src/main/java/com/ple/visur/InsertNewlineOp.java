package com.ple.visur;

public class InsertNewlineOp implements Operator {
  @Override
  public void execute(Object opInfo) {
    EditorModelCoupler emc = ServiceHolder.editorModelCoupler;
    String editorContent = emc.document().getContent();
    int ca = emc.session().getCA();
    String contentBeforeChar = editorContent.substring(0, ca);
    String contentAfterChar = editorContent.substring(ca, editorContent.length());
    String resultingEditorContent = contentBeforeChar + "\n" + contentAfterChar;
    emc.session().putCA(++ca);
    emc.document().putContent(resultingEditorContent);
    SessionText.record(emc, "\n");
    Quantum cursorQuantum = emc.session().getCursorQuantum();
    int[] bounds = cursorQuantum.getBoundaries(ca, emc.document().getNextLineIndices(), emc.session().getSpan(), false);
    emc.session().putCursorQuantumStartAndScroll(bounds[0]);
    emc.session().putCursorQuantumEndAndScroll(bounds[1]);
  }
}
