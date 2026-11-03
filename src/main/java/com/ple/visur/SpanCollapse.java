package com.ple.visur;

/**
 * The one place that decides what "there is nothing to select" means: a collapsed range leaves no span to keep, so
 * the span becomes zero.
 *
 * <p>Before <a href="../../../../docs/wayfinder/span-and-count/issues/01-bound-seam-and-tests.md">ticket 01</a> this
 * rule was spelled three times — {@code SetSpanOp} and {@code ChangeCursorQuantumOp} each compared the bounds they
 * had just computed, and {@code DocumentQuantum#getBoundaries} wrote {@code putSpan(0)} itself whenever the document
 * was empty. Quantum bound computation writes no state now, which is what lets it be tested at several spans in a
 * row.
 */
final class SpanCollapse {
  private SpanCollapse() {}

  /**
   * Zeroes the span when {@code bounds} select nothing.
   *
   * @return whether there was nothing to select, so a caller that also writes a nonzero span knows not to
   */
  static boolean zeroIfNothingSelected(EditorModelCoupler emc, int[] bounds) {
    boolean nothingSelected = bounds[0] == bounds[1];
    if(nothingSelected) {
      emc.putSpan(0);
    }
    return nothingSelected;
  }
}
