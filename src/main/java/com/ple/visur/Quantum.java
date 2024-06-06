package com.ple.visur;

import io.vertx.core.shareddata.Shareable;

import java.util.ArrayList;

public abstract class Quantum implements Shareable {
  abstract int[] getBoundaries(String editorContent, ArrayList<Integer> newlineIndices, boolean includeTail); //newlineIndices are only needed in CustomQuantums
  abstract int move(String editorContent, ArrayList<Integer> newlineIndices, MovementVector m, int[] bounds);
  abstract String getName();
  int quantumStart() {
    EditorModelCoupler emc = ServiceHolder.editorModelCoupler;
    ExecutionDataStack eds = emc.getExecutionDataStack();
    String otherQuantumName = (String)eds.pop();
    String otherQuantumNameWithoutQuotes = otherQuantumName.substring(1, otherQuantumName.length() - 1);
    QuantumNameToQuantum quantumNameToQuantum = emc.getQuantumNameToQuantum();
    Quantum otherQuantum = quantumNameToQuantum.get(otherQuantumNameWithoutQuotes);
    int distanceOfCurrentQuantumBounds = getQuantumBoundsLength();
    int distanceOfOtherQuantumBounds = otherQuantum.getQuantumBoundsLength();
    Quantum longerQuantum;
    Quantum shorterQuantum;
    if(distanceOfCurrentQuantumBounds > distanceOfOtherQuantumBounds) {
      longerQuantum = this;
      shorterQuantum = otherQuantum;
    } else {
      longerQuantum = otherQuantum;
      shorterQuantum = this;
    }
    System.out.println("quantumStart called. CurrentQ = " + getName() + ". OtherQ = " + otherQuantumNameWithoutQuotes);
    System.out.println("longer quantum = " + longerQuantum.getName() + "; shorter quantum = " + shorterQuantum.getName());
    return 0;
  }

  int quantumEnd() {
    EditorModelCoupler emc = ServiceHolder.editorModelCoupler;
    Quantum currentQuantum = emc.getCurrentQuantum();
    String currentQuantumName = currentQuantum.getName();
    ExecutionDataStack eds = emc.getExecutionDataStack();
    String otherQuantumName = (String)eds.pop();
    String otherQuantumNameWithoutQuotes = otherQuantumName.substring(1, otherQuantumName.length() - 1); //did this just to make sure I don't get any unexpected behavior since currentQuantumName is not wrapped in extra quotes
    System.out.println("quantumEnd called. CurrentQ = " + currentQuantumName + ". OtherQ = " + otherQuantumNameWithoutQuotes);
    return 0;
  }

  int getQuantumBoundsLength() {
    EditorModelCoupler emc = ServiceHolder.editorModelCoupler;
    int[] bounds = getBoundaries(emc.getEditorContent(), emc.getNewlineIndices(), false);
    return bounds[1] - bounds[0];
  }

}
