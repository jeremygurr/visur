package CursorPositionDC;

import DataClass.*;

import java.util.ArrayList;
import java.util.HashMap;

public class RCXCYAndNLDC extends CompoundDataClass {
  public RCXCYAndNLDC(int requiredSetValues) {
    super(requiredSetValues);
  }

  @Override
  public CompoundDataClassBrick makeBrick(String name, ArrayList<OuterDataClassBrick> outers, PrimitiveDataClassBrick... reusablePDCBs) {
    CompoundDataClassBrick rcxcyAndNLDCB = CompoundDataClassBrick.make(name, outers, this, new HashMap<>());
    HashMap<String, DataClassBrick> rcxcyAndNLInners = new HashMap<>();

    PrimitiveDataClassBrick nlDCB = reusablePDCBs[0];
    PrimitiveDataClassBrick rcxDCB = reusablePDCBs[1];

    rcxDCB.putOuter(rcxcyAndNLDCB);
    WholeNumberDC wholeNumberDC = (WholeNumberDC) getInner("wholeNumber");
    PrimitiveDataClassBrick cyDCB = wholeNumberDC.makeBrick("cy", new ArrayList<>());
    cyDCB.putOuter(rcxcyAndNLDCB);

    nlDCB.putOuter(rcxcyAndNLDCB);

    rcxcyAndNLInners.put("rcx", rcxDCB);
    rcxcyAndNLInners.put("cy",  cyDCB);
    rcxcyAndNLInners.put("nl", nlDCB);

    return rcxcyAndNLDCB.getInitializedBrickFromInners(rcxcyAndNLInners);
  }

  @Override
  public Result<DataClassBrick> calcInternal(String name, DataClassBrick outerAsBrick) {
    return null;
  }

}
