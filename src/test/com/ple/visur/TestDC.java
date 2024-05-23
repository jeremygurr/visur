package com.ple.visur;

import CursorPositionDC.*;
import DataClass.*;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;


public class TestDC {
  CursorPositionDCHolder cursorPositionDCHolder = CursorPositionDCHolder.make();
  final CompoundDataClassBrick cursorPositionDCB = cursorPositionDCHolder.cursorPositionDC.makeBrick();

  @Test void dcMakeBrick() {
    //cursorPositionDC.makeBrick
    CompoundDataClassBrick cxcycaDCB = (CompoundDataClassBrick) cursorPositionDCB.getInner("cxcyca");
    CompoundDataClassBrick cxcyDCB = (CompoundDataClassBrick)cxcycaDCB.getInner("cxcy");
    assertFalse(cursorPositionDCB.isComplete());
    //test cxcycaDC.makeBrick
    assertFalse(cursorPositionDCB.getInner("cxcyca").isComplete());
    //test wholeNumberListDC.makeBrick
    assertFalse(cursorPositionDCB.getInner("ni").isComplete());
    //test wholePairDC.makeBrick
    assertFalse(cxcycaDCB.getInner("cxcy").isComplete());
    //test wholeNumberDC.makeBrick
    assertFalse(cxcycaDCB.getInner("ca").isComplete());
    assertFalse(cxcyDCB.getInner("cx").isComplete());
    assertFalse(cxcyDCB.getInner("cy").isComplete());
    //makeBrick should set outer of current inner being constructed
    assertEquals(cxcycaDCB, cxcyDCB.getOuter());
    DataClassBrick caDCB = cxcycaDCB.getInner("ca");
    assertEquals(cxcycaDCB, caDCB.getOuter());
    //ensure that all values are set after cursorPositionDC.makeBrick gets called initially

  }


  @Test void pdcbPutSafe() {
    int cx = 4;
    int cy = 0;
    CompoundDataClassBrick cxcycaDCB = (CompoundDataClassBrick) cursorPositionDCB.getInner("cxcyca");
    CompoundDataClassBrick cxcyDCB = (CompoundDataClassBrick) cxcycaDCB.getInner("cxcy");
    PrimitiveDataClassBrick cxDCB = (PrimitiveDataClassBrick) cxcyDCB.getInner("cx");
    PrimitiveDataClassBrick cyDCB = (PrimitiveDataClassBrick) cxcyDCB.getInner("cy");
    PrimitiveDataClassBrick caDCB = (PrimitiveDataClassBrick) cxcycaDCB.getInner("ca");

    //cxcy can be set when ca is unset
    Result r = cxDCB.putSafe(cx);
    assertNull(r.getError());
    r = cyDCB.putSafe(cy);
    assertNull(r.getError());
    assertEquals(cx, cxDCB.get().getVal());
    assertEquals(cy, cyDCB.get().getVal());

    //ni can be set always (because cursorPositionDC.minimumRequiredSetValues == 2)
    ArrayList<Integer> newlineIndices = new ArrayList<>();
    newlineIndices.add(11);
    newlineIndices.add(24);
    PrimitiveDataClassBrick niDCB = (PrimitiveDataClassBrick) cursorPositionDCB.getInner("ni");
    r = niDCB.putSafe(newlineIndices);
    assertNull(r.getError());
    assertEquals(newlineIndices, niDCB.get().getVal());

    //ca can be set when cxcy is set and no conflicts exist
    int ca = 4;
    r = caDCB.putSafe(ca);
    assertNull(r.getError());
    assertEquals(ca, caDCB.get().getVal());

    //ca CAN'T be set when cxcy is set and conflicts DO exist
    int previousCA = ca;
    ca = 14;
    r = caDCB.putSafe(ca);
    assertNotNull(r.getError());
    assertEquals(previousCA, caDCB.get().getVal());

    //cxcy can be set when ca is set and no conflicts exist
    cxDCB.remove();
    cyDCB.remove();
    caDCB.putSafe(ca);
    cx = 2;
    cy = 1;
    r = cxDCB.putSafe(cx);
    assertNull(r.getError());
    r = cyDCB.putSafe(cy);
    assertNull(r.getError());
    assertEquals(cx, cxDCB.get().getVal());
    assertEquals(cy, cyDCB.get().getVal());

    //cxcy CAN'T be set when ca is set and conflicts DO exist
    int previousCX = cx;
    int previousCY = cy;
    cx = 1;
    cy = 0;
    r = cxDCB.putSafe(cx);
    assertNotNull(r.getError());
    r = cyDCB.putSafe(cy);
    assertNotNull(r.getError());
    assertEquals(previousCX, cxDCB.get().getVal());
    assertEquals(previousCY, cyDCB.get().getVal());

    //ca can be set when cxcy is unset
    cxDCB.remove();
    cyDCB.remove();
    ca = 30;
    r = caDCB.putSafe(ca);
    assertNull(r.getError());
    assertEquals(ca, caDCB.get().getVal());

  }

  @Test void pdcbPutForce() {
    int cx = 10;
    int cy = 0;
    CompoundDataClassBrick cxcycaDCB = (CompoundDataClassBrick) cursorPositionDCB.getInner("cxcyca");
    CompoundDataClassBrick cxcyDCB = (CompoundDataClassBrick) cxcycaDCB.getInner("cxcy");
    PrimitiveDataClassBrick cxDCB = (PrimitiveDataClassBrick) cxcyDCB.getInner("cx");
    PrimitiveDataClassBrick cyDCB = (PrimitiveDataClassBrick) cxcyDCB.getInner("cy");
    PrimitiveDataClassBrick caDCB = (PrimitiveDataClassBrick) cxcycaDCB.getInner("ca");

    //cxcy can be set when ca is unset
    Result r = cxDCB.putForce(cx);
    assertNull(r.getError());
    r = cyDCB.putForce(cy);
    assertNull(r.getError());
    assertEquals(cx, cxDCB.get().getVal());
    assertEquals(cy, cyDCB.get().getVal());

    //ni can be set always (because cursorPositionDC.minimumRequiredSetValues == 2)
    ArrayList<Integer> newlineIndices = new ArrayList<>();
    newlineIndices.add(11);
    newlineIndices.add(24);
    PrimitiveDataClassBrick niDCB = (PrimitiveDataClassBrick) cursorPositionDCB.getInner("ni");
    r = niDCB.putForce(newlineIndices);
    assertNull(r.getError());
    assertEquals(newlineIndices, niDCB.get().getVal());

    //ca can be set when cxcy is set and no conflicts exist
    int ca = 10;
    r = caDCB.putForce(ca);
    assertNull(r.getError());
    assertEquals(ca, caDCB.get().getVal());

    //ca CAN'T be set when cxcy is set and conflicts DO exist, but cxcy needs to be UNSET
    ca = 14;
    r = caDCB.putForce(ca);
    assertNull(r.getError());
    assertFalse(cxcyDCB.isComplete());
    assertEquals(14, caDCB.get().getVal());

    //cxcy can be set when ca is set and no conflicts exist
    cxDCB.remove();
    cyDCB.remove();
    caDCB.putForce(ca);
    cx = 2;
    cy = 1;
    r = cxDCB.putForce(cx);
    assertNull(r.getError());
    assertTrue(caDCB.isComplete());
    r = cyDCB.putForce(cy);
    assertNull(r.getError());
    assertEquals(cx, cxDCB.get().getVal());
    assertEquals(cy, cyDCB.get().getVal());
    assertTrue(caDCB.isComplete());

    //cxcy CAN'T be set when ca is set and conflicts DO exist
    cx = 1;
    cy = 0;
    r = cxDCB.putForce(cx);
    assertNull(r.getError());
    assertTrue(cxcyDCB.isComplete());
    r = cyDCB.putForce(cy);
    assertTrue(cxcyDCB.isComplete());
    assertNull(r.getError());
    assertEquals(cx, cxDCB.get().getVal());
    assertEquals(cy, cyDCB.get().getVal());
    caDCB = (PrimitiveDataClassBrick) cxcycaDCB.getInner("ca");
    assertFalse(caDCB.isComplete());

    //ca can be set when cxcy is unset
    cxDCB.remove();
    cyDCB.remove();
    ca = 30;
    r = caDCB.putForce(ca);
    assertNull(r.getError());
    assertEquals(ca, caDCB.get().getVal());


  }

  @Test void dcbGetOrCalc() {
    int ca = 0;
    ArrayList<Integer> newlineIndices = new ArrayList<>();
    newlineIndices.add(11);
    newlineIndices.add(24);
    PrimitiveDataClassBrick niDCB = (PrimitiveDataClassBrick) cursorPositionDCB.getInner("ni");
    niDCB.putSafe(newlineIndices);
    CompoundDataClassBrick cxcycaDCB = (CompoundDataClassBrick) cursorPositionDCB.getInner("cxcyca");
    CompoundDataClassBrick cxcyDCB = (CompoundDataClassBrick) cxcycaDCB.getInner("cxcy");
    PrimitiveDataClassBrick cxDCB = (PrimitiveDataClassBrick) cxcyDCB.getInner("cx");
    PrimitiveDataClassBrick cyDCB = (PrimitiveDataClassBrick) cxcyDCB.getInner("cy");
    PrimitiveDataClassBrick caDCB = (PrimitiveDataClassBrick) cxcycaDCB.getInner("ca");

    //caDCB.getOrCalc when caDCB is set to 0
    caDCB.putForce(ca);
    Result r = caDCB.getOrCalc();
    caDCB = (PrimitiveDataClassBrick) r.getVal();
    assertEquals(ca, caDCB.getVal());

    //caDCB.getOrCalc when cxcyDCB is set to (5, 0) [should be 5]
    int cx = 5;
    int cy = 0;
    cxDCB.putForce(cx);
    cyDCB.putForce(cy);
    assertTrue(cxcyDCB.isComplete());
    r = caDCB.getOrCalc();
    assertNull(r.getError());
    caDCB = (PrimitiveDataClassBrick) r.getVal();
    assertEquals(5, caDCB.get().getVal());

    //cxcyDCB.getOrCalc when cxcyDCB is set to (5, 0)
    r = cxDCB.getOrCalc();
    assertNull(r.getError());
    cxDCB = (PrimitiveDataClassBrick) r.getVal();
    assertEquals(5, cxDCB.get().getVal());
    r = cyDCB.getOrCalc();
    assertNull(r.getError());
    cyDCB = (PrimitiveDataClassBrick) r.getVal();
    assertEquals(0, cyDCB.get().getVal());

    //cxcyDCB.getOrCalc when caDCB is set to 12 [should be (0, 1)]
    caDCB.putForce(12);
    r = cxDCB.getOrCalc();
    assertNull(r.getError());
    cxDCB = (PrimitiveDataClassBrick) r.getVal();
    assertEquals(0, cxDCB.get().getVal());
    r = cyDCB.getOrCalc();
    assertNull(r.getError());
    cyDCB = (PrimitiveDataClassBrick) r.getVal();
    assertEquals(1, cyDCB.get().getVal());

    //cxcyDCB.getOrCalc when caDCB is set to 13 [should be (1, 1)]
    caDCB.putForce(13);
    assertFalse(cxcyDCB.isComplete());
    r = cxDCB.getOrCalc();
    assertNull(r.getError());
    cxDCB = (PrimitiveDataClassBrick) r.getVal();
    assertEquals(1, cxDCB.get().getVal());
    r = cyDCB.getOrCalc();
    assertNull(r.getError());
    cyDCB = (PrimitiveDataClassBrick) r.getVal();
    assertEquals(1, cyDCB.get().getVal());

    //cxcyDCB.getOrCalc when caDCB is set to 22 [should be (10, 1)]
    caDCB.putForce(22);
    assertFalse(cxcyDCB.isComplete());
    r = cxDCB.getOrCalc();
    assertNull(r.getError());
    cxDCB = (PrimitiveDataClassBrick) r.getVal();
    assertEquals(10, cxDCB.get().getVal());
    r = cyDCB.getOrCalc();
    assertNull(r.getError());
    cyDCB = (PrimitiveDataClassBrick) r.getVal();
    assertEquals(1, cyDCB.get().getVal());

  }

}
