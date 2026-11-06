package com.ple.visur;

import io.vertx.core.shareddata.Shareable;

import java.util.ArrayList;

public abstract class Quantum implements Shareable {
  /** Apply the editor spec's separator rule after this quantum computes its own bounds. */
  public final int[] getBoundaries(int ca, ArrayList<Integer> newlineIndices, int span, boolean includeTail) {
    int[] bounds = boundsFor(ca, newlineIndices, span, includeTail);
    VisurVar separatorState = emc.session().getGlobalVar("includeSeparators");
    boolean includeSeparators = separatorState == null || Boolean.TRUE.equals(separatorState.getVal());
    return includeSeparators ? Separators.including(emc.getEditorContent(), bounds,
      emc.getQuantumNameToQuantum().separatorsFor(getName())) : bounds;
  }

  abstract public int[] boundsFor(int ca, ArrayList<Integer> newlineIndices, int span, boolean includeTail); // newlineIndices are only needed in CustomQuantums
  abstract int move(String editorContent, ArrayList<Integer> newlineIndices, MovementVector m);
  abstract String getName();
  EditorModelCoupler emc = ServiceHolder.editorModelCoupler;

  /**
   * What one keystroke of an operator acts on: the selection's width, taken as many times as the count says. Three
   * words selected and {@code 3 d} is nine words, because the count multiplies the span rather than replacing it.
   *
   * <p>Measured from where the selection starts, so an operator always includes what {@code s} showed, and the count
   * never widens what is on screen. Only the end of the content caps it: the scope fence belongs to search and is not
   * consulted here.
   *
   * <p>A count of zero needs no branch. It asks these bound functions for a span of nothing, which is the empty range
   * every quantum already answers with, and the operators refuse to act on an empty range.
   */
  int[] operatorExtent() {
    int units = CountPrefix.countOrOne(emc) * emc.getSpan();
    return getBoundaries(emc.session().getCursorQuantumStart(), emc.getNextLineIndices(), units, false);
  }

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
    emc.session().putVirtualCX(emc.getCX());
    int[] newCursorBounds = cursorQuantum.getBoundaries(ca, emc.getNextLineIndices(), spanMinimumOne, false);
    if(actualSpan == 0) {
      newCursorBounds[1] = newCursorBounds[0];
    }
    emc.session().putCursorQuantumStartAndScroll(newCursorBounds[0]);
    emc.session().putCursorQuantumEndAndScroll(newCursorBounds[1]);
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
    emc.session().putVirtualCX(emc.getCX());
    int[] newCursorBounds = cursorQuantum.getBoundaries(ca, emc.getNextLineIndices(), spanMinimumOne, false);
    emc.session().putCursorQuantumStartAndScroll(newCursorBounds[0]);
    emc.session().putCursorQuantumEndAndScroll(newCursorBounds[1]);
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
   * <p>Which quantum is coarser is a fact about nesting (a character is inside a word is inside a line), and the editor
   * spec declares it by listing its quantums finest first — so the frame is whichever of the two comes later in that
   * list. Measuring the two, at span 1 or at the live span, only approximates nesting and ties where nesting does not:
   * a one-character word and the character in it measure the same, and a tie used to hand the frame to the operand,
   * so {@code ] q} on a one-character word selected a word where the key had asked for a character.
   *
   * <p>Rank decides which is coarser; being under the cursor decides whether it can frame at all. A quantum the cursor
   * is not in covers nothing — a word quantum on a blank line, or between two words — and framing with it would make
   * {@code ] w} select an empty range there instead of walking to the next word, so the roles swap and the coarser
   * quantum stays the one selected.
   *
   * <p>This is not the model's {@code scopeQuantum}, which is the fence a search is confined to.
   */
  private Quantum[] getFrameAndCursorQuantums() {
    ExecutionDataStack eds = emc.session().getExecutionDataStack();
    String otherQuantumName = (String)eds.pop();
    QuantumNameToQuantum quantumNameToQuantum = emc.getQuantumNameToQuantum();
    Quantum otherQuantum = quantumNameToQuantum.get(otherQuantumName);
    int rankingOfCurrentQuantum = quantumNameToQuantum.rankOf(getName());
    int rankingOfOtherQuantum = quantumNameToQuantum.rankOf(otherQuantumName);
    Quantum frameQuantum;
    Quantum cursorQuantum;
    if(rankingOfCurrentQuantum > rankingOfOtherQuantum) {
      frameQuantum = this;
      cursorQuantum = otherQuantum;
    } else {
      frameQuantum = otherQuantum;
      cursorQuantum = this;
    }
    if(!frameQuantum.hasQuantumAtCursor()) {
      Quantum absentFrame = frameQuantum;
      frameQuantum = cursorQuantum;
      cursorQuantum = absentFrame;
    }
    return new Quantum[]{frameQuantum, cursorQuantum};
  }

  /**
   * Whether this quantum is under the cursor at all, which is not the same as how coarsest it is: a word quantum covers
   * nothing on a blank line and nothing between two words. Measured at span 1 because presence is a question about the
   * cursor's neighbourhood, not about how much text is selected.
   */
  boolean hasQuantumAtCursor() {
    int[] bounds = getBoundaries(emc.getCA(), emc.getNextLineIndices(), 1, false);
    return bounds[1] > bounds[0];
  }

}
