package DataClass;

import java.util.ArrayList;
import java.util.HashSet;

public class PrimitiveDataClassBrick extends DataClassBrick {
  private final PrimitiveDataClass pdc;
  private DataFormBrick dfb;
  private boolean isReadOnly;

  private PrimitiveDataClassBrick(String name, ArrayList<OuterDataClassBrick> outers, DataFormBrick dfb, PrimitiveDataClass pdc, boolean isReadOnly) {
    super(pdc, outers, name);
    this.pdc = pdc;
    this.dfb = dfb;
    this.isReadOnly = isReadOnly;
  }
  public static PrimitiveDataClassBrick make(String name, ArrayList<OuterDataClassBrick> outers, DataFormBrick dfb, PrimitiveDataClass pdc, boolean isReadOnly) {
    return new PrimitiveDataClassBrick(name, outers, dfb, pdc, isReadOnly);
  }
  public DataFormBrick getDFB() {
    return dfb;
  }
  public PrimitiveDataClass getPDC() {
    return pdc;
  }

  public Result<PrimitiveDataClassBrick> getOrCalc() {
    return getOrCalc(new HashSet<>());
  }

  public Result<PrimitiveDataClassBrick> getOrCalc(HashSet<DataClassBrick> dcbsAlreadySearched) {
    Result<PrimitiveDataClassBrick> r = Result.make();
    Result<Object> resObj;
    if (isComplete()) {
      r = Result.make(this, null);
    } else {
      resObj = calcFromOuter();
      if(resObj.getError() != null) {
        dcbsAlreadySearched.add(this);
        r = calcNeighbor(dcbsAlreadySearched);
        if (r.getError() == null) {
          r = getOrCalc(new HashSet<>());
        }
      }
    }
    return r;
  }

  private Result<Object> calcFromOuter() {
    Result<Object> outersCalcResult = Result.make(null, "no outers exist for this brick");
    for(OuterDataClassBrick outer : getOuters()) {
      outersCalcResult = outer.getOrCalc(getName());
      if(outersCalcResult.getError() == null) {
        break;
      } else if(outer.getOuters() != null) {
        for (OuterDataClassBrick outerOfOuter : outer.getOuters()) {
          outersCalcResult = outerOfOuter.getOrCalc(getName());
          if(outersCalcResult.getError() == null) break;
        }
      }
    }
    return outersCalcResult;
  }

  public Result<PrimitiveDataClassBrick> cacheVal(Object resultingVal) {
    Result r = Result.make(null, "val equals null");
    if(resultingVal != null) {
      DataFormBrick dfb = DataFormBrick.make(pdc.defaultDF, resultingVal);
      putDFB(dfb);
      r = Result.make(this, null);
    }
    return r;
  }

  private void putVal(Object val) {
    DataFormBrick thisDFBVal = DataFormBrick.make(pdc.defaultDF, val);
    putDFB(thisDFBVal);
  }

  public void put(Object val) {
    putVal(val);
    HashSet<DataClassBrick> dcbsAlreadyChecked = new HashSet<>();
    dcbsAlreadyChecked.add(this);
    removePossibleConflicts(dcbsAlreadyChecked);
  }

  @Override
  public void removePossibleConflicts(HashSet<DataClassBrick> dcbsAlreadyChecked) {
    if(!isReadOnly) {
      ArrayList<OuterDataClassBrick> outers = getOuters();
      for (OuterDataClassBrick outer : outers) {
        if(!dcbsAlreadyChecked.contains(outer)) {
          outer.removePossibleConflicts(dcbsAlreadyChecked);
        }
      }
    }
  }

  @Override
  public boolean isComplete() {
    return getDFB() != null;
  }

  @Override
  public boolean isEmpty() {
    return !isComplete();
  }

  public Object getVal() {
    return getDFB().getVal();
  }

  public Result<Object> get() {
    Object v = null;
    String error = null;
    if (getDFB() != null) {
      v = getDFB().getVal();
    } else {
      error = "value not set";
    }
    return Result.make(v, error);
  }

  public void putDFB(DataFormBrick newDFB) {
    dfb = newDFB;
  }

  @Override
  public boolean containsName(String targetName) {
    return getName().equals(targetName);
  }

  @Override
  public Result remove() {
    String error = null;
    if(!isReadOnly()) {
      putDFB(null);
    } else {
      error = "pdcb " + getName() + " is read-only";
    }
    return Result.make(null, error);
  }

  @Override
  public void removeConflicts(String name, Object val) {
    for(OuterDataClassBrick outer : outers) {
      outer.removeConflicts(name, val);
    }
  }

  public boolean isReadOnly() {
    return isReadOnly;
  }

  public Result<PrimitiveDataClassBrick> calcNeighbor(HashSet<DataClassBrick> dcbsAlreadySearched) {
    Result r;
    HashSet<PrimitiveDataClassBrick> unsetNeighbors = getAllUnsetNeighborsFromOuters(dcbsAlreadySearched);
    HashSet<PrimitiveDataClassBrick> unsetNeighborsWithUniqueOuters = getUnsetNeighborsWithUniqueOuters(unsetNeighbors);
    r = calcNeighborInternal(dcbsAlreadySearched, unsetNeighborsWithUniqueOuters);
    return r;
  }

  private HashSet<PrimitiveDataClassBrick> getUnsetNeighborsWithUniqueOuters(HashSet<PrimitiveDataClassBrick> unsetNeighbors) {
    HashSet<PrimitiveDataClassBrick> unsetNeighborsWithUniqueOuters = new HashSet<>();
    for(PrimitiveDataClassBrick neighbor : unsetNeighbors) {
      ArrayList<OuterDataClassBrick> outersOfNeighbor = neighbor.getOuters();
      if(outersOfNeighbor != null && outersOfNeighbor.size() > 1) {
        unsetNeighborsWithUniqueOuters.add(neighbor);
      }
    }
    return unsetNeighborsWithUniqueOuters;
  }

  private Result calcNeighborInternal(HashSet<DataClassBrick> dcbsAlreadySearched, HashSet<PrimitiveDataClassBrick> unsetNeighborsWithUniqueOuter) {
    Result<PrimitiveDataClassBrick> r = Result.make(null, "calcNeighborInternal failed");
    for(PrimitiveDataClassBrick unsetNeighbor : unsetNeighborsWithUniqueOuter) {
      r = unsetNeighbor.getOrCalc(dcbsAlreadySearched);
      dcbsAlreadySearched.add(unsetNeighbor);
    }
    return r;
  }

}
