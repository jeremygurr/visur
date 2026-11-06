package com.ple.visur;

public class LiteralStringOp implements Operator {

  public static LiteralStringOp make() {
    return new LiteralStringOp();
  }

  @Override
  public void execute(Object opInfo) {
    String opInfoAsString = (String) opInfo;
    String opInfoWithoutQuotes = opInfoAsString.substring(1, opInfoAsString.length() - 1);
    ServiceHolder.editorModelCoupler.session().putOnExecutionDataStack(opInfoWithoutQuotes);
  }

}
