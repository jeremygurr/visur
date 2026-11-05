package com.ple.visur;

import io.vertx.rxjava3.core.Vertx;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class ToggleSeparatorsOpTest {
  private Vertx vertx;
  private EditorModelCoupler emc;

  @BeforeEach
  void setUp() {
    vertx = Vertx.vertx();
    ServiceHolder.init(vertx.sharedData());
    ServiceHolder.initializerService.initializeEditorModel();
    emc = ServiceHolder.editorModelCoupler;
    emc.putEditorContent("one two");
    emc.putCA(0);
    emc.putCX(0);
    emc.putCY(0);
    emc.putVirtualCX(0);
    emc.putSpan(1);
    emc.putCursorQuantum(emc.getQuantumNameToQuantum().get("word"));
  }

  @AfterEach
  void tearDown() {
    vertx.close();
  }

  @Test
  void togglingOffAndOnChangesOnlySeparatorInclusion() {
    Quantum word = emc.getQuantumNameToQuantum().get("word");
    ToggleSeparatorsOp toggle = new ToggleSeparatorsOp();

    assertArrayEquals(new int[]{0, 4}, word.getBoundaries(0, emc.getNextLineIndices(), 1, false));
    toggle.execute(null);
    assertArrayEquals(new int[]{0, 3}, word.getBoundaries(0, emc.getNextLineIndices(), 1, false));
    toggle.execute(null);
    assertArrayEquals(new int[]{0, 4}, word.getBoundaries(0, emc.getNextLineIndices(), 1, false));
    assertEquals("word", emc.getCursorQuantum().getName());
    assertEquals(1, emc.getSpan());
  }

  private static void assertEquals(Object expected, Object actual) {
    org.junit.jupiter.api.Assertions.assertEquals(expected, actual);
  }
}
