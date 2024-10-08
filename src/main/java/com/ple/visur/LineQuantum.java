package com.ple.visur;

import java.util.ArrayList;

public class LineQuantum extends Quantum {

  EditorModelCoupler emc = ServiceHolder.editorModelCoupler;

  @Override
  public int[] getBoundaries(int ca, ArrayList<Integer> nl, int span, boolean includeTail) {
    int[] bounds = new int[]{ca, ca};
    int cy = emc.getCY();
    bounds[0] = cy > 0 ? nl.get(cy - 1) : 0;;
    if(span > 0) {
      bounds[1] = cy == nl.size() - 1 ? nl.get(cy) : nl.get(cy) - 1;;
    } else if(isInMiddleOfQuantum(ca)) {
      bounds[1] = bounds[0];
    } else {
      bounds[0] = ca;
      bounds[1] = ca;
    }
    return bounds;
  }

  @Override
  public int move(String editorContent, ArrayList<Integer> newlineIndices, MovementVector mv) {
    BrickVisurVar caBVV = (BrickVisurVar)emc.getGlobalVar("ca");
    int ca = (int)caBVV.getVal();
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

  private boolean isInMiddleOfQuantum(int bound) {
    String editorContent = emc.getEditorContent();
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
