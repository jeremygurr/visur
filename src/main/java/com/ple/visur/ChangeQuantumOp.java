package com.ple.visur;

public class ChangeQuantumOp implements Operator {

  @Override
  public void execute(Object opInfo) {
    EditorModelService ems = ServiceHolder.editorModelService;
    String quantumName = (String)ems.getExecutionDataStack().pop();
    Quantum targetQuantum = ems.getQuantumMap().get(quantumName);
    String editorContent = ems.getEditorContent();
    int contentX = ems.getGlobalVar("contentX").getInt();
    int contentY = ems.getGlobalVar("contentY").getInt();
    int[] bounds = targetQuantum.getBoundaries(editorContent, contentX, contentY);
    ems.putQuantumStart(bounds[0]);
    ems.putQuantumEnd(bounds[1]);
  }

}