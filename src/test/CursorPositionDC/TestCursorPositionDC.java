package CursorPositionDC;

import DataClass.CompoundDataClassBrick;
import DataClass.LayeredDataClassBrick;
import DataClass.OuterDataClassBrick;
import DataClass.PrimitiveDataClassBrick;
import org.junit.Before;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Order;
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


    rcxDCB = wholeNumberDC.makeBrick("rcx", rcxOuters);
    nlDCB = wholeNumberDC.makeBrick("nl", nlOuters);
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

  }

//  @BeforeEach
//  void removeLDCB() {
//    cursorPositionDCB.remove();
//    assertFalse(cursorPositionDCB.isComplete());
//  }
//
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
    ArrayList<OuterDataClassBrick> coordinatesOuters = coordinatesDCB.getOuters();
    assertTrue(coordinatesOuters.size() == 1);
    assertTrue(coordinatesOuters.containsName("cursorPosition"));

    ArrayList<OuterDataClassBrick> caAndNLOuters = caAndNLDCB.getOuters();
    assertTrue(caAndNLOuters.size() == 1);
    assertTrue(caAndNLOuters.containsName("coordinates"));

    ArrayList<OuterDataClassBrick> rcxcyAndNLOuters = rcxcyAndNLDCB.getOuters();
    assertTrue(rcxcyAndNLOuters.size() == 1);
    assertTrue(rcxcyAndNLOuters.containsName("coordinates"));

    ArrayList<OuterDataClassBrick> nlOuters = nlDCB.getOuters();
    assertTrue(rcxcyAndNLOuters.size() == 2);
    assertTrue(nlOuters.containsName("caAndNL"));
    assertTrue(nlOuters.containsName("rcxcyAndNL"));

    ArrayList<OuterDataClassBrick> caOuters = caDCB.getOuters();
    assertTrue(rcxcyAndNLOuters.size() == 1);
    assertTrue(caOuters.containsName("caAndNL"));

    ArrayList<OuterDataClassBrick> rcxOuters = rcxDCB.getOuters();
    assertTrue(rcxOuters.size() == 1);
    assertTrue(rcxOuters.containsName("rcxcyAndNL"));
    assertTrue(rcxOuters.containsName("rcxAndLO"));

    ArrayList<OuterDataClassBrick> cyOuters = cyDCB.getOuters();
    assertTrue(cyOuters.size() == 1);
    assertTrue(cyOuters.containsName("rcxcyAndNL"));

  }

//  @Test
//  void isComplete() {
//
//  }
//
//  @Test
//  void putWhenNotComplete() {
//    caDCB.put(0);
//    caAndNLDCB.putInner("ca", caDCB);
//  }
//
//  @Test
//  void putWhenComplete() {
//
//  }
//
}
