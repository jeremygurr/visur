package com.ple.visur;

public class LiteralNumberOp implements Operator {
  public static LiteralNumberOp make() {
    return new LiteralNumberOp();
  }

  @Override
  public void execute(Object opInfo) {
    ServiceHolder.editorModelCoupler.session().putOnExecutionDataStack(opInfo);
  }
}
