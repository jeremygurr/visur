package com.ple.visur;

public class SearchInPreviousDirectionOp implements Operator {
  @Override
  public void execute(Object opInfo) {
    EditorModelCoupler emc = ServiceHolder.editorModelCoupler;
    boolean previousDirectionWasForward = emc.session().getPreviousSearchDirectionWasForward();
    if(previousDirectionWasForward) {
      SearchForwardOp searchForwardOp = new SearchForwardOp();
      searchForwardOp.execute(opInfo);
      emc.session().putPreviousSearchDirectionWasForward(true);
    } else {
      SearchBackwardOp searchBackwardOp = new SearchBackwardOp();
      searchBackwardOp.execute(opInfo);
      emc.session().putPreviousSearchDirectionWasForward(false);
    }
  }
}
