package DataClass;


public class PrimitiveDataClassBrick extends DataClassBrick {
  private final PrimitiveDataClass pdc;
  private DataFormBrick val;
    private PrimitiveDataClassBrick(CompoundDataClassBrick outer, PrimitiveDataClass pdc, DataFormBrick val) {
        super(pdc, outer);
        this.pdc = pdc;
        this.val = val;
    }
    public static PrimitiveDataClassBrick make(CompoundDataClassBrick outer, PrimitiveDataClass pdc, DataFormBrick val) {
        return new PrimitiveDataClassBrick(outer, pdc, val);
    }
    public DataFormBrick getDFB() {
        return val;
    }
    public PrimitiveDataClass getPDC() {
      return pdc;
    }

  @Override
  public Result<DataClassBrick> getOrCalculate(String name, DCHolder dcHolder) {
    return outer.getOrCalculate(name, dcHolder);
  }

  @Override
  public boolean isComplete() {
    return true;
  }
}
