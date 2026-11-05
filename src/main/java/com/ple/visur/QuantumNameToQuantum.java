package com.ple.visur;

import io.vertx.core.shareddata.Shareable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * A document type's quantums and the facts that are true of them: what each one is called, what separates it from the
 * next one, and how coarse it is.
 *
 * <p>Coarseness is the insertion order — a quantum put later contains the ones put earlier — so {@code ] w} decides
 * which of two quanta is the frame by comparing positions in this list rather than by measuring how much text each one
 * happens to cover. Measuring is a proxy for nesting that ties exactly where nesting does not, and a tie used to hand
 * the frame to whichever quantum the key naming order happened to supply.
 */
public class QuantumNameToQuantum implements Shareable {
  HashMap<String, Quantum> map = new HashMap<>();
  ArrayList<String> namesInNestingOrder = new ArrayList<>();
  HashMap<String, String> separators = new HashMap<>();

  public Quantum get(String qName) {
    return map.get(qName);
  }

  /**
   * @param separators the characters that may separate this quantum from the next one, empty when nothing separates
   *                   it: a character has no separator, and nothing follows a document
   */
  public void put(String qName, Quantum specificQuantumImplementation, String separators) {
    map.put(qName, specificQuantumImplementation);
    namesInNestingOrder.add(qName);
    this.separators.put(qName, separators);
  }

  /** Finest first. The index of a name here is its nesting rank. */
  public List<String> getNames() {
    return namesInNestingOrder;
  }

  /**
   * @throws IllegalArgumentException when the spec does not have this quantum — a rank is a fact about a spec, so the
   *                                  honest answer for an absent quantum is a failure rather than a number that will
   *                                  silently sort it
   */
  public int rankOf(String qName) {
    int rank = namesInNestingOrder.indexOf(qName);
    if(rank == -1) {
      throw new IllegalArgumentException("the editor spec has no quantum named " + qName + "; it has " + namesInNestingOrder);
    }
    return rank;
  }

  public String separatorsFor(String qName) {
    return separators.get(qName);
  }

}
