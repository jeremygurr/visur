package com.ple.visur;

import java.util.ArrayList;

public class CharacterQuantum extends Quantum {
  EditorModelCoupler emc = ServiceHolder.editorModelCoupler;
  @Override
  public int[] getBoundaries(int ca, ArrayList<Integer> nextLineIndices, int span, boolean includeTail) {
    return boundsFor(emc.getEditorContent(), nextLineIndices, ca, emc.getCY(), span);
  }

  /**
   * The selection for a span of characters starting at {@code ca}, as {@code [start, end)}.
   *
   * <p>Span N covers N characters. The selection runs across line breaks — wrapping is a display concern, and
   * {@code nl}'s last entry is the end of the content, not the end of a line — and clamps there. At or past the end
   * of the content there is nothing to select, so the range collapses.
   *
   * @param content not read; {@code nl}'s last entry is the end of the content. It is part of the signature so all
   *                four quanta's seam takes the same arguments
   * @param cy      not read, for the same reason
   */
  static int[] boundsFor(String content, ArrayList<Integer> nl, int ca, int cy, int span) {
    int contentEnd = nl.get(nl.size() - 1);
    if (ca >= contentEnd) {
      return new int[]{ca, ca};
    }
    if (span <= 0) {
      return new int[]{ca, ca};
    }
    return new int[]{ca, Math.min(ca + span, contentEnd)};
  }

  @Override
  public int move(String editorContent, ArrayList<Integer> nl, MovementVector mv) {
    int ca = emc.getCA();
    int span = emc.getSpan();
    if(mv.dx != 0) {
      if(mv.dx > 0) {
        ca = moveRight(nl, span);
      } else {
        ca = moveLeft();
      }
    }
    if(mv.dy != 0) {
      if(mv.dy > 0) {
        ca = moveDown(nl, span);
      } else {
        ca = moveUp(nl);
      }
    }
    return ca;
  }

  private int moveRight(ArrayList<Integer> nl, int span) {
    int ca = emc.getCA();
    int contentEnd = nl.get(nl.size() - 1);
    int endLimit = span > 0 ? contentEnd - 1 : contentEnd;
    if(ca + 1 <= endLimit) {
      emc.putCA(++ca);
      emc.putVirtualCX(emc.getCX());
    }
    return ca;
  }

  private int moveLeft() {
    int ca = emc.getCA();
    if(ca > 0) {
      emc.putCA(--ca);
      emc.putVirtualCX(emc.getCX());
    }
    return ca;
  }

  private int moveDown(ArrayList<Integer> nl, int span) {
    int cx = emc.getCX();
    int vcx = emc.getVirtualCX();
    int cy = emc.getCY();
    int canvasWidth = emc.getCanvasWidth();
    int[] shortBounds = RelativeLineBoundCalculator.getShort(cx, cy, nl);
    boolean isOnLastShortLineInLongLine = shortBounds[1] - shortBounds[0] < canvasWidth;
    boolean isAtEndOfEditorContent = getIsAtEndOfEditorContent(nl, cy, span, isOnLastShortLineInLongLine);
    boolean shouldIncrementCY = isOnLastShortLineInLongLine && !isAtEndOfEditorContent;
    if (shouldIncrementCY) {
      emc.putCY(++cy);
      vcx = vcx % canvasWidth;
    } else if (!isAtEndOfEditorContent) {
      vcx += canvasWidth;
    }
    int longLineLength = RelativeLineBoundCalculator.getLongLineLength(cy, nl);
    boolean shouldDecrementLongLineLength = nl.get(nl.size() - 1).equals(nl.get(nl.size() - 2)) && cy < nl.size() - 1;
    int longLineLimit = span > 0 || shouldDecrementLongLineLength ? longLineLength - 1 : longLineLength;
    cx = Math.min(vcx, longLineLimit);
    emc.putCX(cx);
    emc.putVirtualCX(vcx);
    return emc.getCA();
  }

  private boolean getIsAtEndOfEditorContent(ArrayList<Integer> nl, int cy, int span, boolean isOnLastShortOfLong) {
    if(isOnLastShortOfLong) {
      int editorContentLength = nl.get(nl.size() - 1);
      int lastLineStart = nl.size() > 1 ? nl.get(nl.size() - 2) : 0;
      boolean lastCharInContentIsNewline = editorContentLength == lastLineStart;
      int lastValidCY = span > 0 && lastCharInContentIsNewline ? nl.size() - 2 : nl.size() - 1;
      return cy >= lastValidCY;
    } else {
      return false;
    }
  }

  private int moveUp(ArrayList<Integer> nextLineIndices) {
    int cx = emc.getCX();
    int cy = emc.getCY();
    int canvasWidth = emc.getCanvasWidth();
    int vcx = emc.getVirtualCX();
    boolean isOnFirstLongLine = cy == 0;
    boolean isOnFirstShortLine = cx < canvasWidth;
    boolean shouldDecrementCY = !isOnFirstLongLine && isOnFirstShortLine;
    if(shouldDecrementCY) {
      emc.putCY(--cy);
      int previousLongLineLength = RelativeLineBoundCalculator.getLongLineLength(cy, nextLineIndices);
      int[] shortBoundsOfLastShortInPreviousLine = RelativeLineBoundCalculator.getShort(previousLongLineLength, cy, nextLineIndices);
      vcx = vcx % canvasWidth + shortBoundsOfLastShortInPreviousLine[0];
      cx = Math.min(vcx, previousLongLineLength - 1);
    } else if(!isOnFirstShortLine) {
      vcx -= canvasWidth;
      cx = vcx;
    }
    emc.putCX(cx);
    emc.putVirtualCX(vcx);
    return emc.getCA();
  }

  @Override
  public String getName() {
    return "character";
  }

}
