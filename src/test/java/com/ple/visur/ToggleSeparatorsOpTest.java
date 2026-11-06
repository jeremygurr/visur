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
    emc.session().putVirtualCX(0);
    emc.putSpan(1);
    emc.putCursorQuantum(emc.getQuantumNameToQuantum().get("word"));
  }

  @AfterEach
  void tearDown() {
    vertx.close();
  }

  /**
   * The key, not the operator. The operator has passed its own test since it was written, while the binding asked the
   * keymap for a two-character {@code \\} that no client ever sends, and {@code \} did nothing in the real editor.
   * Ticket 02's "through keypresses" was the criterion that went untested.
   */
  @Test
  void theBackslashKeyTogglesSeparatorsBothWays() {
    Quantum word = emc.getQuantumNameToQuantum().get("word");
    int[] withSeparator = word.getBoundaries(0, emc.getNextLineIndices(), 1, false);

    press("\\");
    assertArrayEquals(new int[]{0, 3}, word.getBoundaries(0, emc.getNextLineIndices(), 1, false));

    press("\\");
    assertArrayEquals(withSeparator, word.getBoundaries(0, emc.getNextLineIndices(), 1, false));
  }

  private void press(String key) {
    ServiceHolder.editorModelCoupler.session().clearPendingCount();
    KeyWasPressedVerticle.press(ServiceHolder.editorModelCoupler, KeyPressed.from(key), key);
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
