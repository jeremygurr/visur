package DataClass;

import java.util.HashMap;
import java.util.Map;

public class CompoundDataClassBrick extends DataClassBrick {
  private CompoundDataClass cdc;
  HashMap<String, DataClassBrick> inners;
  private CompoundDataClassBrick(CompoundDataClassBrick outer, CompoundDataClass cdc, HashMap<String, DataClassBrick> inners) {
      super(cdc, outer);
      this.cdc = cdc;
      this.inners = inners;
  }
  public static CompoundDataClassBrick make(CompoundDataClassBrick outer, CompoundDataClass cdc, HashMap<String, DataClassBrick> inners) {
      return new CompoundDataClassBrick(outer, cdc, inners);
  }
  public DataClassBrick getInner(String name) {
      return inners.get(name);
  }
  public Result put(String innerName, DataClassBrick innerVal) {
    String error = null;
    if(innerVal != null) {
      CompoundDataClass thisCDC = getCDC();
      if(!thisCDC.brickCanBeSet(innerName, inners)) {
        error = "putInner failed, too many values set";
      }
      innerVal.putName(innerName);
    }
    if(error == null || outer == null) {
      inners.put(innerName, innerVal);
      return Result.make(null, error);
    } else {
      return outer.put(innerName, innerVal);
    }
  }

  public CompoundDataClass getCDC() {
    return cdc;
  }

  @Override
  public Result<DataClassBrick> getOrCalc(String name, DCHolder dcHolder) {
    DataClassBrick inner = getInner(name);
    Result<DataClassBrick> r;
    //if inner's value is set, return result whose value equals getInner(name)
    if(inner == null) {
      r = calc(name, dcHolder);
    } else {
      r = Result.make(inner, null);
    }
    CompoundDataClassBrick outer = getOuter();
    if(r.getError() != null && outer != null) {
      r = outer.getOrCalc(name, dcHolder);
    }
    return r;
  }

  @Override
  public Result put(String name) {
    return null;
  }

  @Override
  public Result forcePut(String name) {
    return null;
  }

  public Result<DataClassBrick> calc(String name, DCHolder dcHolder) {
    Result r;
    CompoundDataClassBrick outerBrick = getOuter();
    boolean canSet = cdc.checkCanSet(this, outerBrick, dcHolder);
    if(canSet) {
      r = dc.calcInternal(name, this, dcHolder);
      if (r.getError() != null && outer != null) {
        r = outer.calc(name, dcHolder);
      }
    } else {
      r = Result.make(null, "can't set");
    }
    return r;
  }

  @Override
  public boolean isComplete() {
    int numberOfSetValues = 0;
    for(DataClassBrick inner : inners.values()) {
      if(inner != null && inner.isComplete()) {
        numberOfSetValues++;
      }
    }
    return numberOfSetValues >= cdc.minimumRequiredSetValues;
  }

  public Result removeInner(String name) {
    DataClassBrick inner = inners.get(name);
    if(inner instanceof PrimitiveDataClassBrick) {
      PrimitiveDataClassBrick innerAsPDCB = (PrimitiveDataClassBrick) inner;
      innerAsPDCB.putDFB(null);
      inners.put(name, innerAsPDCB);
    } else if(inner instanceof CompoundDataClassBrick){
      CompoundDataClassBrick innerAsCDCB = (CompoundDataClassBrick) inner;
      for(String innerInnerName : innerAsCDCB.inners.keySet()) {
        innerAsCDCB.removeInner(innerInnerName);
      }
    }
    return Result.make();
  }

  public void putInner(DataClassBrick innerVal) {
    String innerName = innerVal.getName();
    inners.put(innerName, innerVal);
  }

  public Result<Object> get(String name) {
    Object val = null;
    String error = null;
    PrimitiveDataClassBrick inner;
    if(inners.containsKey(name)) {
      inner = (PrimitiveDataClassBrick) getInner(name);
      if (inner instanceof PrimitiveDataClassBrick) {
        if (inner.getDFB() == null) {
          val = null;
        } else {
          val = inner.getDFB().getVal();
        }
      }
    } else {
      //loop through the inners of the inners to try to find the inner whose name matches name
      for(Map.Entry<String, DataClassBrick> currentInner : inners.entrySet()) {
        CompoundDataClassBrick currentInnerAsCDCB = (CompoundDataClassBrick) currentInner;
        Result r = currentInnerAsCDCB.get(name);
        val = r.getVal();
        error = r.getError();
      }
    }
    return Result.make(val, error);
  }

}
