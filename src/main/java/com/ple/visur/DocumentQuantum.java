package com.ple.visur;

import java.util.ArrayList;

public class DocumentQuantum extends Quantum {
  @Override
  public int[] boundsFor(int realCA, ArrayList<Integer> newlineIndices, int span, boolean includeTail) {
    EditorModelCoupler emc = ServiceHolder.editorModelCoupler;
    int[] bounds = boundsFor(emc.getEditorContent(), newlineIndices, realCA, emc.getCY(), span);
    boolean nothingSelectedAndCursorIsNotAlreadyAtTheEnd = span <= 0 && realCA != bounds[0];
    if(nothingSelectedAndCursorIsNotAlreadyAtTheEnd) {
      emc.putCA(0); // legacy side effect: selecting no part of the document parks the cursor at its start
    }
    return bounds;
  }

  /**
   * The whole document, at any span of one or more. Span is not capped at one here: with a single document, "this
   * document and the next two" is the same selection as "this document", and the number stays legal because
   * documents are expected to multiply.
   *
   * <p>With no selection, the cursor belongs at the start of the document, so the range collapses there — or at the
   * cursor itself when the cursor is already at the end of the content.
   *
   * @param content not read; {@code nl}'s last entry is the end of the content. Part of the signature so all four
   *                quanta's seam takes the same arguments.
   * @param cy      not read, for the same reason
   */
  static int[] boundsFor(String content, ArrayList<Integer> nl, int ca, int cy, int span) {
    int contentEnd = nl.get(nl.size() - 1);
    if(span <= 0) {
      return ca == contentEnd ? new int[]{ca, ca} : new int[]{0, 0};
    }
    return new int[]{0, contentEnd};
  }

  @Override
  int move(String editorContent, ArrayList<Integer> newlineIndices, MovementVector m) {
    EditorModelCoupler emc = ServiceHolder.editorModelCoupler;
    int span = emc.getSpan();
    int ca = emc.getCA();
    if(span == 0) {
      if(ca < editorContent.length() && m.dx > 0) {
        ca = editorContent.length();
      } else if(ca == editorContent.length() && m.dx < 0) {
        ca = 0;
      }
    }
    return ca;
  }

  @Override
  String getName() {
    return "document";
  }

}
