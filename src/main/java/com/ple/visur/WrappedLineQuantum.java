package com.ple.visur;

import java.util.ArrayList;

public class WrappedLineQuantum implements Quantum {

  EditorModelCoupler emc = ServiceHolder.editorModelCoupler;

  @Override
  public int[] getBoundaries(String editorContent, ArrayList<Integer> newlineIndices, boolean includeTail) {
    return emc.getCurrentLineBoundaries(editorContent, newlineIndices, includeTail);
  }

  @Override
  public int move(String editorContent, ArrayList<Integer> newlineIndices, MovementVector mv, int[] bounds) {
    CharacterQuantum cq = new CharacterQuantum();
    mv.dy += mv.dx;
    BrickVisurVar caBVV = (BrickVisurVar)emc.getGlobalVar("ca");
    int ca = (int)caBVV.getVal();
    BrickVisurVar cyBVV = (BrickVisurVar)emc.getGlobalVar("cy");
    int cy = (int)cyBVV.getVal();
    boolean lastCharIsNewline = editorContent.charAt(editorContent.length() - 1) == '\n';
    while(mv.dy != 0) {
      if(mv.dy > 0) {
        boolean canGoDown = lastCharIsNewline ? cy < newlineIndices.size() - 1 : cy < newlineIndices.size();
        if(canGoDown) {
          ca = cq.move(editorContent, newlineIndices, mv, bounds);
        } else {
          mv.dy--;
        }
      } else {
        boolean canGoUp = cy > 0;
        if(canGoUp) {
          ca = cq.move(editorContent, newlineIndices, mv, bounds);
        } else {
          mv.dy++;
        }
      }
    }
    return ca;
  }

  @Override
  public String getName() {
    return "wrappedLine";
  }

  @Override
  public int quantumStart() {
    return 0;
  }

  @Override
  public int quantumEnd() {
    return 0;
  }

}
