package com.ple.visur;

public class ChangeCursorQuantumOp implements Operator {

  @Override
  public void execute(Object opInfo) {
    EditorModelCoupler emc = ServiceHolder.editorModelCoupler;
    String cursorQuantumName = getQuantumName(emc);
    if(cursorQuantumName != null) {
      Quantum targetQuantum = emc.getQuantumNameToQuantum().get(cursorQuantumName);
      int[] bounds = targetQuantum.getBoundaries(emc.session().getCA(), emc.document().getNextLineIndices(), emc.session().getSpan(), false);
      emc.session().putCursorQuantum(targetQuantum);
      emc.session().putCursorQuantumStartAndScroll(bounds[0]);
      emc.session().putCursorQuantumEndAndScroll(bounds[1]);
    }
  }

  /**
   * The quantum this word names, either directly ({@code "word"}) or by the session variable that holds it
   * ({@code "tempCursorQuantum"}, the way a command restores what it saved before an insert session).
   *
   * <p>The word is <em>popped</em>, whichever way the lookup goes. It used to be peeked, so the variable branch left it
   * on the stack: Escape from insert mode parked {@code tempCursorQuantum} there, and the next command to take an
   * argument got that instead — which is how {@code ZZ} came to be told to save a file named
   * {@code tempCursorQuantum}, and why saving after typing had never worked. A command's scratch stack is not its
   * argument's mailbox.
   *
   * <p>Null means the word named no quantum, and {@link #execute} then changes nothing, as before.
   */
  public String getQuantumName(EditorModelCoupler emc) {
    ExecutionDataStack eds = emc.session().getExecutionDataStack();
    if(eds.size() == 0) {
      return null;
    }
    String word = (String) eds.pop();
    Quantum namedQuantum = emc.getQuantumNameToQuantum().get(word);
    if(namedQuantum != null) {
      return namedQuantum.getName();
    }
    VisurVar var = emc.session().getGlobalVar(word);
    Object value = var == null ? null : var.getVal();
    return value instanceof Quantum ? ((Quantum) value).getName() : null;
  }

}
