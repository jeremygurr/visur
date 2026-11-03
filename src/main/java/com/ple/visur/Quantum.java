package com.ple.visur;

import io.vertx.core.shareddata.Shareable;

import java.util.ArrayList;

public abstract class Quantum implements Shareable {
  abstract int[] getBoundaries(int ca, ArrayList<Integer> newlineIndices, int span, boolean includeTail); //newlineIndices are only needed in CustomQuantums
  abstract int move(String editorContent, ArrayList<Integer> newlineIndices, MovementVector m);
  abstract String getName();
  EditorModelCoupler emc = ServiceHolder.editorModelCoupler;

  /**
   * The jump {@code [} performs: walk back to the start of the frame, then select the cursor quantum's span from there.
   *
   * <p>No count is read here, unlike {@link #quantumEnd}: a quantum's start edge is the same index whatever the span, so
   * walking back N of them is arithmetic the bound functions do not have yet. {@code 3[} is one quantum back.
   */
  void quantumStart() {
    int actualSpan = emc.getSpan();
    int spanMinimumOne = Math.max(actualSpan, 1);
    Quantum[] frameAndCursorQuantums = getFrameAndCursorQuantums();
    Quantum frameQuantum = frameAndCursorQuantums[0];
    Quantum cursorQuantum = frameAndCursorQuantums[1];
    int ca = emc.getCA();
    int[] frameBounds = frameQuantum.getBoundaries(ca, emc.getNextLineIndices(), spanMinimumOne, false);
    ca = frameBounds[0];
    emc.putCA(ca);
    emc.putVirtualCX(emc.getCX());
    int[] newCursorBounds = cursorQuantum.getBoundaries(ca, emc.getNextLineIndices(), spanMinimumOne, false);
    if(actualSpan == 0) {
      newCursorBounds[1] = newCursorBounds[0];
    }
    emc.putCursorQuantumStartAndScroll(newCursorBounds[0]);
    emc.putCursorQuantumEndAndScroll(newCursorBounds[1]);
    setSmallestQuantum(cursorQuantum);
  }

  /**
   * The jump {@code ]} performs: walk to the end of the frame, then select the cursor quantum's span from there.
   *
   * <p>How far the jump goes is the count, not the span — one quantum per press, {@code 3]} three — while how wide the
   * selection lands is the span. They used to be the same number, so selecting more text moved the cursor further for
   * the same two keys.
   */
  void quantumEnd() {
    int actualSpan = emc.getSpan();
    int spanMinimumOne = Math.max(actualSpan, 1);
    // A jump has to land somewhere, so 0] is ] rather than a cursor off the end of the frame.
    int jumpUnits = Math.max(CountPrefix.countOrOne(emc), 1);
    Quantum[] frameAndCursorQuantums = getFrameAndCursorQuantums();
    Quantum frameQuantum = frameAndCursorQuantums[0];
    Quantum cursorQuantum = frameAndCursorQuantums[1];
    int ca = emc.getCA();
    int[] frameBounds = frameQuantum.getBoundaries(ca, emc.getNextLineIndices(), jumpUnits, false);
    ca = actualSpan > 0 ? frameBounds[1] - 1 : frameBounds[1];
    emc.putCA(ca);
    emc.putVirtualCX(emc.getCX());
    int[] newCursorBounds = cursorQuantum.getBoundaries(ca, emc.getNextLineIndices(), spanMinimumOne, false);
    emc.putCursorQuantumStartAndScroll(newCursorBounds[0]);
    emc.putCursorQuantumEndAndScroll(newCursorBounds[1]);
    setSmallestQuantum(cursorQuantum);
  }

  private void setSmallestQuantum(Quantum smallestQuantum) {
    if(!smallestQuantum.equals(emc.getCursorQuantum())) {
      emc.putCursorQuantum(smallestQuantum);
    }
  }

  /**
   * The two quanta a jump works between: the frame, which the jump walks to the edge of, and the cursor quantum,
   * which is then selected inside it. The operand quantum comes off the execution data stack — it is the quantum key
   * typed after {@code [} or {@code ]}.
   *
   * <p>They are ranked at span 1, not at the span currently selected. Which quantum is coarser is a fact about nesting
   * (a character is inside a word is inside a line); measuring the two at the live span only approximates that, and the
   * approximation ties where both grow one character per span — across blank lines, where a character and a step over a
   * blank line each measure one character. A tie hands the frame to the operand, so {@code ] w} would leave the cursor
   * in a different quantum depending on how much the selection happened to cover.
   *
   * <p>This is not the model's {@code scopeQuantum}, which is the fence a search is confined to.
   */
  private Quantum[] getFrameAndCursorQuantums() {
    ExecutionDataStack eds = emc.getExecutionDataStack();
    String otherQuantumName = (String)eds.pop();
    QuantumNameToQuantum quantumNameToQuantum = emc.getQuantumNameToQuantum();
    Quantum otherQuantum = quantumNameToQuantum.get(otherQuantumName);
    int rankingOfCurrentQuantum = getQuantumBoundsLengthAtSpan(1);
    int rankingOfOtherQuantum = otherQuantum.getQuantumBoundsLengthAtSpan(1);
    Quantum frameQuantum;
    Quantum cursorQuantum;
    if(rankingOfCurrentQuantum > rankingOfOtherQuantum) {
      frameQuantum = this;
      cursorQuantum = otherQuantum;
    } else {
      frameQuantum = otherQuantum;
      cursorQuantum = this;
    }
    return new Quantum[]{frameQuantum, cursorQuantum};
  }

  int getQuantumBoundsLengthAtSpan(int span) {
    EditorModelCoupler emc = ServiceHolder.editorModelCoupler;
    int[] bounds = getBoundaries(emc.getCA(), emc.getNextLineIndices(), span, false);
    return bounds[1] - bounds[0];
  }

}
