package CursorPositionDC;

import DataClass.*;

public class WholeNumberDC extends PrimitiveDataClass {
  @Override
  public PrimitiveDataClassBrick makeBrick(Object val, CompoundDataClassBrick outerBrick, DCHolder dcHolder) {
    PrimitiveDataClassBrick res;
    if(isValidInput(val)) {
      CursorPositionDCHolder cursorPositionDCHolder = (CursorPositionDCHolder) dcHolder;
      DataForm targetDF = cursorPositionDCHolder.javaIntDF;
      res = PrimitiveDataClassBrick.make(this, outerBrick, DataFormBrick.make(targetDF, val));
    } else {
      res = null;
    }
    return res;
  }

  @Override
  public boolean isValidInput(Object val) {
    if(val instanceof Integer && (Integer) val >= 0 || val == null) {
      return true;
    } else {
      return false;
    }
  }
}