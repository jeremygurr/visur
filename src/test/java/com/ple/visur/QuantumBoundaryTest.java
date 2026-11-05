package com.ple.visur;

import io.vertx.rxjava3.core.Vertx;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class QuantumBoundaryTest {
  private Vertx vertx;
  private EditorModelCoupler emc;

  @BeforeEach
  void setUp() {
    vertx = Vertx.vertx();
    ServiceHolder.init(vertx.sharedData());
    ServiceHolder.initializerService.initializeEditorModel();
    emc = ServiceHolder.editorModelCoupler;
    emc.putEditorContent("one two\nthree");
    emc.putCA(0);
    emc.putCX(0);
    emc.putCY(0);
    emc.putVirtualCX(0);
    emc.putSpan(1);
  }

  @AfterEach
  void tearDown() {
    vertx.close();
  }

  @Test
  void wordBoundariesIncludeTheFollowingSpace() {
    Quantum word = emc.getQuantumNameToQuantum().get("word");
    assertArrayEquals(new int[]{0, 4}, word.getBoundaries(0, emc.getNextLineIndices(), 1, false));
  }

  @Test
  void lineBoundariesIncludeTheFollowingNewline() {
    Quantum line = emc.getQuantumNameToQuantum().get("line");
    assertArrayEquals(new int[]{0, 8}, line.getBoundaries(0, emc.getNextLineIndices(), 1, false));
  }
}
