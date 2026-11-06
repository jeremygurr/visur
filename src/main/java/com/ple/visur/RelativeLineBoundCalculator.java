package com.ple.visur;

import java.util.ArrayList;

public class RelativeLineBoundCalculator {

  public static int getLongLineLength(int cy, ArrayList<Integer> nl) {
    int caAtBeginningOfNextLine = nl.get(cy);
    int longLineLength;
    if(cy > 0) {
      longLineLength = caAtBeginningOfNextLine - nl.get(cy - 1);
    } else {
      longLineLength = caAtBeginningOfNextLine;
    }
    return longLineLength;
  }

  public static int[] getShort(int cx, int cy, ArrayList<Integer> nl) {
    EditorModelCoupler emc = ServiceHolder.editorModelCoupler;
    int[] shortBounds = new int[2];
    int canvasWidth = emc.session().getCanvasWidth();
    shortBounds[0] = getShortLineStart(canvasWidth, cx);
    shortBounds[1] = getShortLineEnd(shortBounds[0], canvasWidth, cx, cy, nl);
    return shortBounds;
  }

  private static int getShortLineStart(int canvasWidth, int cx) {
    int shortLineStart = 0;
    for (int i = cx; i > 0; i--) {
      if (i % canvasWidth == 0) {
        shortLineStart = i;
        break;
      }
    }
    return shortLineStart;
  }

  private static int getShortLineEnd(int shortLineStart, int cw, int cx, int cy, ArrayList<Integer> nl) {
    final int longLineLength = getLongLineLength(cy, nl);
    int shortLineEnd = longLineLength;
    if (shortLineEnd != shortLineStart) {
      for (int i = cx + 1; i < longLineLength; i++) {
        if (i % cw == 0) {
          shortLineEnd = i;
          break;
        }
      }
    }
    return shortLineEnd;
  }

}


