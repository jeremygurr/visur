package CursorPositionDC;

import DataClass.DCHolder;
import io.vertx.core.shareddata.Shareable;

public class CursorPositionDCHolder extends DCHolder implements Shareable {

  public final JavaIntDF javaIntDF = new JavaIntDF(0);
  public final IntArrayListDF intArrayListDF = new IntArrayListDF(0);
  public final CursorPositionDC cursorPositionDC = new CursorPositionDC();
  public final CoordinatesDC coordinatesDC = new CoordinatesDC(1);
  public final VirtualDC virtualDC = new VirtualDC(1);
  public final CAAndNLDC caAndNLDC = new CAAndNLDC(2);
  public final RCXCYAndNLDC rcxcyAndNLDC = new RCXCYAndNLDC(3);
  public final VCXAndLLDC vcxAndLLDC = new VCXAndLLDC(2);
  public final RCXAndLODC rcxAndLODC = new RCXAndLODC(2);
  public final WholeNumberDC wholeNumberDC = new WholeNumberDC(javaIntDF);
  public final WholeNumberListDC wholeNumberListDC = new WholeNumberListDC(intArrayListDF);

  public static CursorPositionDCHolder make() {
    return new CursorPositionDCHolder();
  }

  public CursorPositionDCHolder() {
    caAndNLDC.putInner("wholeNumber", wholeNumberDC);
    caAndNLDC.putInner("nl", wholeNumberListDC);
    rcxcyAndNLDC.putInner("wholeNumber", wholeNumberDC);
    rcxcyAndNLDC.putInner("nl", wholeNumberListDC);
    vcxAndLLDC.putInner("wholeNumber", wholeNumberDC);
    rcxAndLODC.putInner("wholeNumber", wholeNumberDC);
    coordinatesDC.putInner("caAndNL", caAndNLDC);
    coordinatesDC.putInner("rcxcyAndNL", rcxcyAndNLDC);
    virtualDC.putInner("vcxAndLL", vcxAndLLDC);
    virtualDC.putInner("rcxAndLO", rcxAndLODC);
    cursorPositionDC.putLayer(coordinatesDC);
    cursorPositionDC.putLayer(virtualDC);
  }

}
