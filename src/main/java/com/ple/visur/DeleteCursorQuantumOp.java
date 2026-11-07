package com.ple.visur;

public class DeleteCursorQuantumOp implements Operator {

  EditorModelCoupler emc = ServiceHolder.editorModelCoupler;

  @Override
  public void execute(Object opInfo) {
    // One guard for both refusals: no selection, or a count of zero, names no text, and an editor that deletes text
    // it never highlighted is an editor nobody trusts with a count.
    int[] extent = emc.session().getCursorQuantum().operatorExtent();
    int startBound = extent[0];
    int endBound = extent[1];
    if(endBound > startBound) {
      String editorContent = emc.document().getContent();
      String deleted = editorContent.substring(startBound, endBound);
      String resultingEditorContent = editorContent.substring(0, startBound) + editorContent.substring(endBound);
      emc.document().putContent(resultingEditorContent);
      // What was removed goes where a yank puts it, so p can put it back. One unnamed register, so d clobbers y by
      // design; named registers are the map's fog.
      emc.session().putGlobalVar("yankRegister", ObjectVisurVar.make(deleted));
      Clipboard.offer(emc.session(), deleted);
      int ca = emc.session().getCA();
      if(ca >= resultingEditorContent.length()) {
        ca = resultingEditorContent.length() > 0 ? resultingEditorContent.length() - 1 : 0;
        emc.session().putCA(ca);
        emc.session().putVirtualCX(emc.session().getCX());
      }
      // The range collapses to the gap the delete made, while the span survives: a window re-derived over the text
      // that slid into the deleted one would be a highlight over text nobody selected, and p (ticket 03) would eat it
      // instead of putting what d removed back. Moving re-derives the window at the span still in the model.
      emc.session().putCursorQuantumStartAndScroll(startBound);
      emc.session().putCursorQuantumEndAndScroll(startBound);
    }
  }

}
