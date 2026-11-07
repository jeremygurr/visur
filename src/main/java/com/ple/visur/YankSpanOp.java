package com.ple.visur;

public class YankSpanOp implements Operator {
  @Override
  public void execute(Object ignored) {
    EditorModelCoupler emc = ServiceHolder.editorModelCoupler;
    String content = emc.document().getContent();
    // count x span, so 3 y takes nine words when three are selected. An empty range — no selection, or a count of
    // zero — yanks nothing and leaves the register holding what it already did.
    int[] extent = emc.session().getCursorQuantum().operatorExtent();
    String text = content.substring(extent[0], extent[1]);
    if (!text.isEmpty()) {
      emc.session().putGlobalVar("yankRegister", ObjectVisurVar.make(text));
      Clipboard.offer(emc.session(), text);
    }
  }
}
