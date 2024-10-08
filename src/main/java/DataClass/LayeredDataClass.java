package DataClass;

import java.util.ArrayList;

public abstract class LayeredDataClass extends OuterDataClass {

  ArrayList<CompoundDataClass> layers = new ArrayList<>();

  public void putLayer(CompoundDataClass cdc) {
    layers.add(cdc);
  }

  public CompoundDataClass getLayer(int i) {
    return layers.get(i);
  }


  @Override
  public Result<Object> calcInternal(String targetName, OuterDataClassBrick thisAsBrick) {
    Result<Object> r = Result.make(null, "no layers exist");
    for(CompoundDataClass layer : layers) {
      r = layer.calcInternal(targetName, thisAsBrick);
    }
    return r;
  }

}
