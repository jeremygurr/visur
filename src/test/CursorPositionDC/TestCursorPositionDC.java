package CursorPositionDC;

import DataClass.CompoundDataClassBrick;
import DataClass.LayeredDataClassBrick;
import DataClass.OuterDataClassBrick;
import DataClass.PrimitiveDataClassBrick;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import static org.junit.Assert.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

public class TestCursorPositionDC {

  static CursorPositionDCHolder cursorPositionDCHolder = new CursorPositionDCHolder();
  static CursorPositionDC cursorPositionDC;
  static LayeredDataClassBrick cursorPositionDCB;
  static CoordinatesDC coordinatesDC;
  static CompoundDataClassBrick coordinatesDCB;
  static CAAndNLDC caAndNLDC;
  static CompoundDataClassBrick caAndNLDCB;
  static RCXCYAndNLDC rcxcyAndNLDC;
  static CompoundDataClassBrick rcxcyAndNLDCB;
  static VirtualDC virtualDC;
  static CompoundDataClassBrick virtualDCB;
  static VCXAndLLDC vcxAndLLDC;
  static CompoundDataClassBrick vcxAndLLDCB;
  static RCXAndLODC rcxAndLODC;
  static CompoundDataClassBrick rcxAndLODCB;
  static WholeNumberDC wholeNumberDC;
  static PrimitiveDataClassBrick caDCB;
  static PrimitiveDataClassBrick rcxDCB;
  static PrimitiveDataClassBrick cyDCB;
  static PrimitiveDataClassBrick vcxDCB;
  static PrimitiveDataClassBrick llDCB;
  static PrimitiveDataClassBrick loDCB;
  static WholeNumberListDC nlDC;
  static PrimitiveDataClassBrick nlDCB;
  static JavaIntDF javaIntDF;

  @BeforeAll
  static void initVars() {
    cursorPositionDC = cursorPositionDCHolder.cursorPositionDC;
    coordinatesDC = cursorPositionDCHolder.coordinatesDC;
    caAndNLDC = cursorPositionDCHolder.caAndNLDC;
    rcxcyAndNLDC = cursorPositionDCHolder.rcxcyAndNLDC;
    virtualDC = cursorPositionDCHolder.virtualDC;
    vcxAndLLDC = cursorPositionDCHolder.vcxAndLLDC;
    rcxAndLODC = cursorPositionDCHolder.rcxAndLODC;
    wholeNumberDC = cursorPositionDCHolder.wholeNumberDC;
    nlDC = cursorPositionDCHolder.wholeNumberListDC;
    javaIntDF = cursorPositionDCHolder.javaIntDF;

    ArrayList<OuterDataClassBrick> nlOuters = new ArrayList<>();
    ArrayList<OuterDataClassBrick> loOuters = new ArrayList<>();
    ArrayList<OuterDataClassBrick> llOuters = new ArrayList<>();
    ArrayList<OuterDataClassBrick> vcxOuters = new ArrayList<>();
    ArrayList<OuterDataClassBrick> cyOuters = new ArrayList<>();
    ArrayList<OuterDataClassBrick> rcxOuters = new ArrayList<>();
    ArrayList<OuterDataClassBrick> caOuters = new ArrayList<>();
    ArrayList<OuterDataClassBrick> rcxAndLOOuters = new ArrayList<>();
    ArrayList<OuterDataClassBrick> vcxAndLLOuters = new ArrayList<>();
    ArrayList<OuterDataClassBrick> virtualOuters = new ArrayList<>();
    ArrayList<OuterDataClassBrick> rcxcyAndNLOuters = new ArrayList<>();
    ArrayList<OuterDataClassBrick> caAndNLOuters = new ArrayList<>();
    ArrayList<OuterDataClassBrick> coordinatesOuters = new ArrayList<>();


    rcxDCB = wholeNumberDC.makeBrick("rcx", rcxOuters, true);
    nlDCB = wholeNumberDC.makeBrick("nl", nlOuters, true);
    cursorPositionDCB = cursorPositionDC.makeBrick(nlDCB, rcxDCB);
    coordinatesDCB = cursorPositionDCB.getLayer(0);
    caAndNLDCB = (CompoundDataClassBrick) coordinatesDCB.getInner("caAndNL");
    rcxcyAndNLDCB = (CompoundDataClassBrick) coordinatesDCB.getInner("rcxcyAndNL");
    virtualDCB = cursorPositionDCB.getLayer(1);
    vcxAndLLDCB = (CompoundDataClassBrick) virtualDCB.getInner("vcxAndLL");
    rcxAndLODCB = (CompoundDataClassBrick) virtualDCB.getInner("rcxAndLO");
    caDCB = (PrimitiveDataClassBrick) caAndNLDCB.getInner("ca");
    cyDCB = (PrimitiveDataClassBrick) rcxcyAndNLDCB.getInner("cy");
    vcxDCB = (PrimitiveDataClassBrick) vcxAndLLDCB.getInner("vcx");
    llDCB = (PrimitiveDataClassBrick) vcxAndLLDCB.getInner("ll");
    loDCB = (PrimitiveDataClassBrick) rcxAndLODCB.getInner("lo");

    coordinatesDCB.putInner("caAndNL", caAndNLDCB);
    coordinatesDCB.putInner("rcxcyAndNL", rcxcyAndNLDCB);

    caAndNLDCB.putInner("ca", caDCB);
    caAndNLDCB.putInner("nl", nlDCB);

    rcxcyAndNLDCB.putInner("rcx", rcxDCB);
    rcxcyAndNLDCB.putInner("cy", cyDCB);
    rcxcyAndNLDCB.putInner("nl", nlDCB);

    virtualDCB.putInner("vcxAndLL", vcxAndLLDCB);
    virtualDCB.putInner("rcxAndLO", rcxAndLODCB);

    vcxAndLLDCB.putInner("vcx", vcxDCB);
    vcxAndLLDCB.putInner("ll", llDCB);

    rcxAndLODCB.putInner("rcx", rcxDCB);
    rcxAndLODCB.putInner("lo", loDCB);

    assertTrue(nlDCB.getIsReadOnly());
    assertTrue(rcxDCB.getIsReadOnly());
    assertFalse(caDCB.getIsReadOnly());
    assertFalse(cyDCB.getIsReadOnly());
    assertFalse(vcxDCB.getIsReadOnly());
    assertFalse(llDCB.getIsReadOnly());
    assertFalse(loDCB.getIsReadOnly());

  }

  @BeforeEach
  void remove() {
    boolean nlDCBWasCompleteBeforeRemoval = nlDCB.isComplete();
    boolean rcxDCBWasCompleteBeforeRemoval = rcxDCB.isComplete();
    cursorPositionDCB.remove();
    assertFalse(cursorPositionDCB.isComplete());
    assertFalse(coordinatesDCB.isComplete());
    assertFalse(virtualDCB.isComplete());
    assertFalse(caDCB.isComplete());
    assertFalse(cyDCB.isComplete());
    assertFalse(vcxDCB.isComplete());
    assertFalse(llDCB.isComplete());
    assertFalse(loDCB.isComplete());
    assertEquals(nlDCBWasCompleteBeforeRemoval, nlDCB.isComplete());
    assertEquals(rcxDCBWasCompleteBeforeRemoval, rcxDCB.isComplete());
  }

  @Test
  void setCursorPositionDCHolder() {
    assertEquals(coordinatesDC, cursorPositionDC.getLayer(0));
    assertEquals(virtualDC, cursorPositionDC.getLayer(1));

    assertEquals(caAndNLDC, coordinatesDC.getInner("caAndNL"));
    assertEquals(rcxcyAndNLDC, coordinatesDC.getInner("rcxcyAndNL"));

    assertEquals(vcxAndLLDC, virtualDC.getInner("vcxAndLL"));
    assertEquals(rcxAndLODC, virtualDC.getInner("rcxAndLO"));

    assertEquals(nlDC, caAndNLDC.getInner("nl"));
    assertEquals(wholeNumberDC, caAndNLDC.getInner("wholeNumber"));

    assertEquals(nlDC, rcxcyAndNLDC.getInner("nl"));
    assertEquals(wholeNumberDC, rcxcyAndNLDC.getInner("wholeNumber"));

    assertEquals(wholeNumberDC, vcxAndLLDC.getInner("wholeNumber"));

    assertEquals(wholeNumberDC, rcxAndLODC.getInner("wholeNumber"));

  }

  @Test
  void setInners() {
    assertEquals("coordinates", cursorPositionDCB.getLayer(0).name);
    assertEquals("virtual", cursorPositionDCB.getLayer(1).name);

    assertNotNull(coordinatesDCB.getInner("caAndNL"));
    assertNotNull(coordinatesDCB.getInner("rcxcyAndNL"));

    assertNotNull(virtualDCB.getInner("vcxAndLL"));
    assertNotNull(virtualDCB.getInner("rcxAndLO"));

    assertNotNull(caAndNLDCB.getInner("ca"));
    assertNotNull(caAndNLDCB.getInner("nl"));

    assertNotNull(rcxcyAndNLDCB.getInner("rcx"));
    assertNotNull(rcxcyAndNLDCB.getInner("cy"));
    assertNotNull(rcxcyAndNLDCB.getInner("nl"));

    assertNotNull(vcxAndLLDCB.getInner("vcx"));
    assertNotNull(vcxAndLLDCB.getInner("ll"));

    assertNotNull(rcxAndLODCB.getInner("rcx"));
    assertNotNull(rcxAndLODCB.getInner("lo"));
  }

  @Test
  void setOuters() {
    ArrayList<OuterDataClassBrick> coordinatesOutersAsBricks = coordinatesDCB.getOuters();
    ArrayList<String> coordinateOuters = getOuterNamesFromBricks(coordinatesOutersAsBricks);
    assertTrue(coordinateOuters.size() == 1);
    assertTrue(coordinateOuters.contains("cursorPosition"));

    ArrayList<OuterDataClassBrick> caAndNLOutersAsBricks = caAndNLDCB.getOuters();
    ArrayList<String> caAndNLOuters = getOuterNamesFromBricks(caAndNLOutersAsBricks);
    assertTrue(caAndNLOuters.size() == 1);
    assertTrue(caAndNLOuters.contains("coordinates"));

    ArrayList<OuterDataClassBrick> rcxcyAndNLOutersAsBricks = rcxcyAndNLDCB.getOuters();
    ArrayList<String> rcxcyAndNLOuters = getOuterNamesFromBricks(rcxcyAndNLOutersAsBricks);
    assertTrue(rcxcyAndNLOuters.size() == 1);
    assertTrue(rcxcyAndNLOuters.contains("coordinates"));

    ArrayList<OuterDataClassBrick> nlOutersAsBricks = nlDCB.getOuters();
    ArrayList<String> nlOuters = getOuterNamesFromBricks(nlOutersAsBricks);
    assertTrue(nlOuters.size() == 2);
    assertTrue(nlOuters.contains("caAndNL"));
    assertTrue(nlOuters.contains("rcxcyAndNL"));

    ArrayList<OuterDataClassBrick> caOutersAsBricks = caDCB.getOuters();
    ArrayList<String> caOuters = getOuterNamesFromBricks(caOutersAsBricks);
    assertTrue(caOuters.size() == 1);
    assertTrue(caOuters.contains("caAndNL"));

    ArrayList<OuterDataClassBrick> rcxOutersAsBricks = rcxDCB.getOuters();
    ArrayList<String> rcxOuters = getOuterNamesFromBricks(rcxOutersAsBricks);
    assertTrue(rcxOuters.size() == 2);
    assertTrue(rcxOuters.contains("rcxcyAndNL"));
    assertTrue(rcxOuters.contains("rcxAndLO"));

    ArrayList<OuterDataClassBrick> cyOutersAsBricks = cyDCB.getOuters();
    ArrayList<String> cyOuters = getOuterNamesFromBricks(cyOutersAsBricks);
    assertTrue(cyOuters.size() == 1);
    assertTrue(cyOuters.contains("rcxcyAndNL"));

  }

  private ArrayList<String> getOuterNamesFromBricks(ArrayList<OuterDataClassBrick> outersAsBricks) {
    ArrayList<String> outerNames = new ArrayList<>();
    for(OuterDataClassBrick outerBrick : outersAsBricks) {
      outerNames.add(outerBrick.getName());
    }
    return outerNames;
  }

  @Test
  void isComplete() {
    assertFalse(cursorPositionDCB.isComplete());
    assertFalse(coordinatesDCB.isComplete());
    assertFalse(virtualDCB.isComplete());
    assertFalse(nlDCB.isComplete());
    assertFalse(caDCB.isComplete());
    assertFalse(rcxDCB.isComplete());
    assertFalse(cyDCB.isComplete());
    assertFalse(vcxDCB.isComplete());
    assertFalse(llDCB.isComplete());
    assertFalse(loDCB.isComplete());

    //coordinates
    //caAndNL
    caDCB.put(0);
    assertTrue(caDCB.isComplete());
    assertFalse(caAndNLDCB.isComplete());

    ArrayList<Integer> nextLineIndices = new ArrayList<>();
    nextLineIndices.add(12);
    nextLineIndices.add(25);
    nextLineIndices.add(32);
    nlDCB.put(nextLineIndices);
    assertTrue(nlDCB.isComplete());
    assertTrue(caAndNLDCB.isComplete());

    assertTrue(coordinatesDCB.isComplete());

    //rcxcyAndNL
    assertTrue(nlDCB.isComplete());
    assertFalse(rcxcyAndNLDCB.isComplete());
    rcxDCB.put(0);
    assertTrue(rcxDCB.isComplete());
    assertFalse(rcxcyAndNLDCB.isComplete());

    cyDCB.put(0);
    assertTrue(cyDCB.isComplete());
    assertTrue(rcxcyAndNLDCB.isComplete());

    assertTrue(coordinatesDCB.isComplete());

    //virtual
    //vcxAndLL
    assertFalse(virtualDCB.isComplete());
    assertFalse(vcxAndLLDCB.isComplete());
    vcxDCB.put(0);
    assertTrue(vcxDCB.isComplete());
    assertFalse(vcxAndLLDCB.isComplete());
    llDCB.put(68);
    assertTrue(llDCB.isComplete());
    assertTrue(vcxAndLLDCB.isComplete());

    assertTrue(virtualDCB.isComplete());

    //rcxAndLO
    assertFalse(rcxAndLODCB.isComplete());
    assertTrue(rcxDCB.isComplete());
    loDCB.put(0);
    assertTrue(loDCB.isComplete());
    assertTrue(rcxAndLODCB.isComplete());
    assertTrue(virtualDCB.isComplete());

    assertTrue(cursorPositionDCB.isComplete());

  }

  @Test
  void putWhenNotComplete() {
    //coordinates
    //set caAndNL when rcxcyAndNL is unset
    assertFalse(caAndNLDCB.isComplete());
    caDCB.put(0);
    assertEquals(0, caDCB.getVal());

    ArrayList<Integer> nl = new ArrayList<>();
    nl.add(5);
    nl.add(10);
    nlDCB.put(nl);

    caAndNLDCB.putInner("ca", caDCB);
    caAndNLDCB.putInner("nl", nlDCB);
    assertEquals(nl, nlDCB.getVal());
    assertTrue(caAndNLDCB.isComplete());
    assertTrue(coordinatesDCB.isComplete());

    caAndNLDCB.remove();

    //set rcxcyAndNL when caAndNL is unset
    assertFalse(caAndNLDCB.isComplete());
    assertFalse(rcxcyAndNLDCB.isComplete());

    rcxDCB.put(0);
    assertEquals(0, rcxDCB.getVal());
    assertFalse(rcxcyAndNLDCB.isComplete());
    cyDCB.put(1);
    assertEquals(1, cyDCB.getVal());
    assertTrue(rcxcyAndNLDCB.isComplete());

    //virtual
    //set vcxAndLL when rcxAndLO is unset
    assertFalse(rcxAndLODCB.isComplete());
    assertFalse(vcxAndLLDCB.isComplete());

    vcxDCB.put(0);
    assertEquals(0, vcxDCB.getVal());
    assertFalse(vcxAndLLDCB.isComplete());
    llDCB.put(10);
    assertEquals(10, llDCB.getVal());
    assertTrue(vcxAndLLDCB.isComplete());

    //set rcxAndLO when vcxAndLL is unset
    vcxAndLLDCB.remove();
    assertFalse(vcxAndLLDCB.isComplete());
    rcxDCB.put(4);
    assertEquals(4, rcxDCB.getVal());
    assertFalse(rcxAndLODCB.isComplete());
    loDCB.put(-1);
    assertEquals(-1, loDCB.getVal());
    assertTrue(rcxAndLODCB.isComplete());

  }

  @Test
  void putWhenComplete() {
    //coordinates
    //setting caAndNL unsets rcxcyAndNL if rcxcyAndNL is set
    rcxDCB.put(0);
    cyDCB.put(1);
    ArrayList<Integer> nl = new ArrayList<>();
    nl.add(13);
    nlDCB.put(nl);
    assertTrue(rcxcyAndNLDCB.isComplete());
    assertFalse(caAndNLDCB.isComplete());
    caDCB.put(0);
    assertFalse(rcxcyAndNLDCB.isComplete());
    assertTrue(caAndNLDCB.isComplete());

    //setting rcxcyAndNL unsets caAndNL if caAndNL is set
    assertTrue(rcxDCB.isComplete());
    assertTrue(nlDCB.isComplete());
    assertFalse(rcxcyAndNLDCB.isComplete());
    assertTrue(caAndNLDCB.isComplete());
    cyDCB.put(2);
    assertEquals(2, cyDCB.getVal());
    assertTrue(rcxcyAndNLDCB.isComplete());
    assertFalse(caAndNLDCB.isComplete());

    //virtual
    //setting vcxAndLL unsets rcxAndLO if rcxAndLO is set
    assertFalse(rcxAndLODCB.isComplete());
    assertTrue(rcxDCB.isComplete());
    loDCB.put(-3);
    assertFalse(vcxAndLLDCB.isComplete());
    assertTrue(rcxAndLODCB.isComplete());
    vcxDCB.put(5);
    assertFalse(vcxAndLLDCB.isComplete());
    assertFalse(rcxAndLODCB.isComplete());
    llDCB.put(15);
    assertTrue(vcxAndLLDCB.isComplete());
    assertFalse(rcxAndLODCB.isComplete());

    //setting rcxAndLO unsets vcxAndLL if vcxAndLL is set
    assertTrue(rcxDCB.isComplete());
    loDCB.put(0);
    assertEquals(0, loDCB.getVal());
    assertFalse(vcxAndLLDCB.isComplete());
    assertTrue(rcxAndLODCB.isComplete());

  }

  @Test void getOrCalc() {
    //1 if nl = [12, 25, 32], rcx = 5, and cy = 1, ca can be calculated = 17
    ArrayList<Integer> nl = new ArrayList<>();
    nl.add(12);
    nl.add(25);
    nl.add(32);
    nlDCB.put(nl);
    rcxDCB.put(5);
    cyDCB.put(1);
    assertEquals(nl, nlDCB.getVal());
    assertEquals(5, rcxDCB.getVal());
    assertEquals(1, cyDCB.getVal());
    caDCB = caDCB.getOrCalc().getVal();
    assertEquals(17, caDCB.getVal());
    assertEquals(nl, nlDCB.getVal());

    //2 if nl = [12, 25, 32], rcx = 3, and cy = 2, ca can be calculated = 28
    rcxDCB.put(3);
    cyDCB.put(2);
    assertTrue(rcxcyAndNLDCB.isComplete());
    assertEquals(3, rcxDCB.getVal());
    assertEquals(2, cyDCB.getVal());
    assertEquals(nl, nlDCB.getVal());
    caDCB = caDCB.getOrCalc().getVal();
    assertEquals(28, caDCB.getVal());

    //3 
  }

}
