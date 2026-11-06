package com.ple.visur;

import java.util.ArrayList;

public class LineQuantum extends Quantum {

  EditorModelCoupler emc = ServiceHolder.editorModelCoupler;

  @Override
  public int[] boundsFor(int ca, ArrayList<Integer> nl, int span, boolean includeTail) {
    return boundsFor(emc.getEditorContent(), nl, ca, emc.getCY(), span);
  }

  /**
   * The selection for a span of {@code span} lines starting at the cursor's line, as {@code [start, end)}.
   *
   * <p>The end is the end of the {@code cy + span - 1}'th line, so a span reaches into the following lines rather
   * than clipping at the current one, and clamps at the end of the content. A line's own newline belongs to that
   * line, except that a content not ending in a newline has no phantom character to include.
   */
  static int[] boundsFor(String content, ArrayList<Integer> nl, int ca, int cy, int span) {
    int[] bounds = new int[]{ca, ca};
    bounds[0] = cy > 0 ? nl.get(cy - 1) : 0;
    if(span > 0) {
      bounds[1] = lineEnd(nl, Math.min(cy + span - 1, nl.size() - 1));
    } else if(isInMiddleOfQuantum(content, ca)) {
      bounds[1] = bounds[0];
    } else {
      bounds[0] = ca;
      bounds[1] = ca;
    }
    return bounds;
  }

  /**
   * The index just past the last character of line {@code line}. Only the phantom line after a trailing newline has
   * no character of its own, so its end is its own start.
   */
  private static int lineEnd(ArrayList<Integer> nl, int line) {
    boolean lastLineHasNoNewlineOfItsOwn = line == nl.size() - 1;
    return lastLineHasNoNewlineOfItsOwn ? nl.get(line) : nl.get(line) - 1;
  }

  @Override
  public int move(String editorContent, ArrayList<Integer> newlineIndices, MovementVector mv) {
    int ca = emc.getCA();
    int span = emc.getSpan();
    if(span > 0) {
      mv.dy += mv.dx;
      mv.dx = 0;
    }
    while(mv.dy != 0) {
      if(mv.dy > 0) {
        ca = moveDown(newlineIndices);
        mv.dy--;
      } else {
        ca = moveUp(newlineIndices);
        mv.dy++;
      }
    }
    while(mv.dx != 0) {
      if(mv.dx > 0) {
        ca = zeroQuantumMoveRight(ca, editorContent, newlineIndices);
        mv.dx--;
      } else {
        ca = zeroQuantumMoveLeft(ca, newlineIndices);
        mv.dx++;
      }
      emc.putCA(ca);
      emc.putVirtualCX(emc.getCX());
    }
    return ca;
  }

  private int moveDown(ArrayList<Integer> nl) {
    int cy = emc.getCY();
    if(cy < nl.size() - 1) {
      emc.putCY(++cy);
      int newLongLineLength = RelativeLineBoundCalculator.getLongLineLength(cy, nl);
      int vcx = emc.getVirtualCX();
      int lineEndLimit = newLongLineLength - 1;
      int cx = Math.min(vcx, lineEndLimit);
      emc.putCX(cx);
    }
    return emc.getCA();
  }

  private int moveUp(ArrayList<Integer> nl) {
    int cy = emc.getCY();
    if(cy > 0) {
      emc.putCY(--cy);
      int newLongLineLength = RelativeLineBoundCalculator.getLongLineLength(cy, nl);
      int vcx = emc.getVirtualCX();
      int lineEndLimit = cy == nl.size() - 1 ? newLongLineLength : newLongLineLength - 1;
      int cx = Math.min(vcx, lineEndLimit);
      emc.putCX(cx);
    }
    return emc.getCA();
  }

  /** Whether {@code bound} sits inside a line rather than at its start. */
  private static boolean isInMiddleOfQuantum(String editorContent, int bound) {
    if(bound > 0 && bound < editorContent.length()) {
      boolean matchExistsBefore = editorContent.charAt(bound - 1) != '\n';
      boolean matchExistsAfter = editorContent.charAt(bound) != '\n';
      return matchExistsBefore && matchExistsAfter;
    } else {
      return false;
    }
  }

  private int zeroQuantumMoveRight(int ca, String editorContent, ArrayList<Integer> nl) {
    int destination = ca;
    boolean startingCharIsNewline = editorContent.charAt(ca) == '\n';
    int contentEnd = editorContent.length() - 1;
    if (startingCharIsNewline && ca < contentEnd) {
      destination++;
    } else if(ca < contentEnd) {
      int cy = emc.getCY();
      boolean lastCharInContentIsNewline = nl.get(nl.size() - 1) == '\n';
      boolean cyIsOnLastLongInContent = cy == nl.size() - 1;
      boolean shouldDecrementNextLineIndex = !(lastCharInContentIsNewline || cyIsOnLastLongInContent);
      destination = shouldDecrementNextLineIndex ? nl.get(cy) - 1 : nl.get(cy);
    }
    return destination;
  }

  private int zeroQuantumMoveLeft(int ca, ArrayList<Integer> nl) {
    int destination = ca;
    if(ca > 0) {
      int cy = emc.getCY();
      boolean previousCharIsNewline = nl.contains(ca) && ca < nl.get(nl.size() - 1);
      if(previousCharIsNewline) {
        destination--;
      } else {
        destination = cy > 0 ? nl.get(cy - 1) : 0;
      }
    }
    return destination;
  }

  @Override
  public String getName() {
    return "line";
  }

}
