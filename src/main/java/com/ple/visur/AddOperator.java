package com.ple.visur;

public class AddOperator implements Operator {
  @Override
  public void execute(Object opInfo) {
    EditorModelCoupler ems = ServiceHolder.editorModelCoupler;
    ExecutionDataStack es = ems.session().getExecutionDataStack();
    int a = (int)es.pop();
    int b = (int)es.pop();
    es.push(a + b);
  }
}
