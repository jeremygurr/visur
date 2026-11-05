package com.ple.visur;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The four quantums are data belonging to an editor specification, not four classes registered by hand.
 *
 * <p>A specification declares its quantums in nesting order — finest first — and each entry says what separates that
 * quantum from the next one. The order <em>is</em> the nesting rank: {@code ] w} asks which of two quanta is coarser,
 * and the answer is which one comes later in the list, not which one happens to measure more text.
 *
 * <p>The two-quantum test is the one that earns the layer. A specification with exactly one instance of four hardcoded
 * classes is a wrapper; a specification that can build a registry, an order and a validator for quantums it has never
 * seen is a seam.
 */
class EditorSpecTest {

  @Test
  void thePlaintextSpecIsFourQuantumsFinestFirst() {
    QuantumNameToQuantum registry = EditorSpec.plaintext().registry();
    assertEquals(List.of("character", "word", "line", "document"), registry.getNames());
    assertEquals(0, registry.rankOf("character"));
    assertEquals(1, registry.rankOf("word"));
    assertEquals(2, registry.rankOf("line"));
    assertEquals(3, registry.rankOf("document"));
  }

  @Test
  void eachPlaintextQuantumDeclaresWhatSeparatesItFromTheNext() {
    QuantumNameToQuantum registry = EditorSpec.plaintext().registry();
    assertEquals("", registry.separatorsFor("character"));
    assertEquals(" \t", registry.separatorsFor("word")); // a newline ends a line; it does not separate two words
    assertEquals("\n", registry.separatorsFor("line"));
    assertEquals("", registry.separatorsFor("document")); // nothing follows the document
  }

  @Test
  void aTwoQuantumSpecBuildsItsOwnRegistryOrderAndSeparators() {
    EditorSpec spec = new EditorSpec("two",
      new EditorSpec.Entry("character", new CharacterQuantum(), ""),
      new EditorSpec.Entry("line", new LineQuantum(), "\n"));

    QuantumNameToQuantum registry = spec.registry();

    assertEquals(List.of("character", "line"), registry.getNames());
    assertTrue(registry.get("line") instanceof LineQuantum);
    assertEquals(0, registry.rankOf("character"));
    assertEquals(1, registry.rankOf("line"));
    assertEquals("\n", registry.separatorsFor("line"));
  }

  /** A rank is a fact about a spec, so a quantum the spec does not have has no rank — loudly, not as -1. */
  @Test
  void aQuantumTheSpecDoesNotHaveHasNoRank() {
    QuantumNameToQuantum registry = EditorSpec.plaintext().registry();
    assertThrows(IllegalArgumentException.class, () -> registry.rankOf("digit"));
  }

  @Test
  void aKeymapNamingAQuantumTheSpecDoesNotHaveFailsAtBoot() {
    KeyToQuantumName keyToQuantumName = new KeyToQuantumName();
    keyToQuantumName.put("q", "character");
    keyToQuantumName.put("w", "word");
    EditorSpec.plaintext().validate(keyToQuantumName); // the real keymap is fine, so the failure below is the check

    keyToQuantumName.put("d", "digit");
    IllegalStateException failure = assertThrows(IllegalStateException.class,
      () -> EditorSpec.plaintext().validate(keyToQuantumName));
    assertTrue(failure.getMessage().contains("d"), failure.getMessage());
    assertTrue(failure.getMessage().contains("digit"), failure.getMessage());
  }

  /** The plaintext entries' quantums are the classes they always were; only where they are written down changed. */
  @Test
  void thePlaintextRegistryHoldsTheSameFourQuantumImplementations() {
    QuantumNameToQuantum registry = EditorSpec.plaintext().registry();
    assertTrue(registry.get("character") instanceof CharacterQuantum);
    assertTrue(registry.get("word") instanceof RegexQuantum);
    assertTrue(registry.get("line") instanceof LineQuantum);
    assertTrue(registry.get("document") instanceof DocumentQuantum);
    assertEquals("word", registry.get("word").getName());
  }
}
