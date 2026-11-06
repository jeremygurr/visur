package com.ple.visur;

/**
 * {@code P} inserts the register's content <em>before</em> the selection, in contrast to {@code p} (which replaces the
 * selection). It is non-destructive — the text it covers is not removed, the register is not updated — and the
 * selection is shifted right by the inserted length so the same text remains selected.
 *
 * <p>With nothing selected, {@code P} inserts at the caret and leaves span at 0 — the same visual result as {@code p}
 * because there is no selection to disturb.
 */
public class PasteBeforeYankRegisterOp implements Operator {
  @Override
  public void execute(Object ignored) {
    EditorModelCoupler emc = ServiceHolder.editorModelCoupler;
    VisurVar register = emc.session().getGlobalVar("yankRegister");
    if (register == null) return;
    String value = (String) register.getVal();
    String text = value.replace("\r\n", "\n");
    if (text.isEmpty()) return;

    String content = emc.getEditorContent();
    int start = emc.getCursorQuantumStart();
    int end = emc.getCursorQuantumEnd();
    String pasted = text.repeat(CountPrefix.countOrOne(emc));
    if (pasted.isEmpty()) return;

    emc.putEditorContent(content.substring(0, start) + pasted + content.substring(start));

    int cursor = start + pasted.length() - 1;
    emc.putCA(cursor);

    // Keep the selection covering the same text, shifted right by the inserted length, or re-seat at span 0 when
    // there was nothing selected — TextInsertion.insertAt always collapses, which is wrong for the first case.
    if (end > start) {
      emc.putCursorQuantumStartAndScroll(start + pasted.length());
      emc.putCursorQuantumEndAndScroll(end + pasted.length());
    } else {
      int[] bounds = emc.getCursorQuantum().getBoundaries(cursor, emc.getNextLineIndices(), 0, false);
      emc.putCursorQuantumStartAndScroll(bounds[0]);
      emc.putCursorQuantumEndAndScroll(bounds[1]);
    }
    emc.putIsAtQuantumStart(true);
  }
}