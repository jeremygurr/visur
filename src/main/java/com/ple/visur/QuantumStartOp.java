package com.ple.visur;

public class QuantumStartOp implements Operator {
  @Override
  public void execute(Object opInfo) {
    EditorModelCoupler emc = ServiceHolder.editorModelCoupler;
    Quantum cursorQuantum = emc.session().getCursorQuantum();
    cursorQuantum.quantumStart(); //internally sets ca and quantum bounds
  }
}
