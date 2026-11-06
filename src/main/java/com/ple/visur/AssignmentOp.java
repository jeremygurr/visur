package com.ple.visur;

import DataClass.DataClassBrick;
import DataClass.PrimitiveDataClassBrick;

public class AssignmentOp implements Operator {

  public static AssignmentOp make() {
    return new AssignmentOp();
  }

  @Override
  public void execute(Object opInfo) {
    EditorModelCoupler emc = ServiceHolder.editorModelCoupler;
    ExecutionDataStack es = emc.session().getExecutionDataStack();
    String nameToPut = (String)es.pop();
    String nameOfValueToRetrieve = (String)es.pop();
    VisurVar vvOfValueRetrieved = emc.session().getGlobalVar(nameOfValueToRetrieve);
    Object valueRetrieved = vvOfValueRetrieved.getVal();
    VisurVar vvResult;
    if(vvOfValueRetrieved instanceof BrickVisurVar) {
      vvResult = BrickVisurVar.make((PrimitiveDataClassBrick) valueRetrieved);
    } else {
      vvResult = ObjectVisurVar.make(valueRetrieved);
    }
    emc.session().putGlobalVar(nameToPut, vvResult);
  }

}
