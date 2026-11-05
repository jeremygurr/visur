package com.ple.visur;

import java.util.ArrayList;
import java.util.List;

/**
 * What a document type declares about its quantums: which ones it has, in nesting order, and what separates each from
 * the next.
 *
 * <p>Visur edits one kind of document today — plaintext — so this exists for the second kind rather than the first. A
 * JSON document wants quantums plaintext has no use for (a key-value pair, an array element) and separators it has
 * never seen (a comma), and none of that should require editing {@link InitializerService}.
 *
 * <p>Keybindings are deliberately not declared here: which key selects which quantum stays in {@link KeyToQuantumName},
 * so a specification can be chosen by document type without changing any binding.
 */
public class EditorSpec {
  final String name;
  final Entry[] entries; // finest first

  EditorSpec(String name, Entry... entries) {
    this.name = name;
    this.entries = entries;
  }

  /** One quantum and the characters that separate it from the next one, empty when nothing does. */
  static class Entry {
    final String name;
    final Quantum quantum;
    final String separators;

    Entry(String name, Quantum quantum, String separators) {
      this.name = name;
      this.quantum = quantum;
      this.separators = separators;
    }
  }

  /**
   * The only specification that exists: today's four quantums, in the order they nest, with the separators that make a
   * selection cover whole units.
   *
   * <p>The word pattern {@code \S+} lives here with the word quantum rather than in the initializer, so everything that
   * makes a document type a document type is in one object.
   */
  static EditorSpec plaintext() {
    return new EditorSpec("plaintext",
      new Entry("character", new CharacterQuantum(), ""),
      new Entry("word", new RegexQuantum("word", "\\S+"), " \t"),
      new Entry("line", new LineQuantum(), "\n"),
      new Entry("document", new DocumentQuantum(), ""));
  }

  QuantumNameToQuantum registry() {
    QuantumNameToQuantum quantumNameToQuantum = new QuantumNameToQuantum();
    for(Entry entry : entries) {
      quantumNameToQuantum.put(entry.name, entry.quantum, entry.separators);
    }
    return quantumNameToQuantum;
  }

  /**
   * The four quantum names, finest first. Used by {@link #validate} so it does not build a registry per key.
   */
  List<String> names() {
    List<String> names = new ArrayList<>();
    for(Entry entry : entries) {
      names.add(entry.name);
    }
    return names;
  }

  /**
   * Fails loudly when a key names a quantum this specification does not have.
   *
   * <p>The alternative is a dead key: {@code e} would press, do nothing, and leave the user to discover that their
   * document type has no lines. A specification is chosen at boot, so a mismatch is a startup error and nobody has to
   * be told in a message box.
   */
  void validate(KeyToQuantumName keyToQuantumName) {
    for(String key : keyToQuantumName.getKeys()) {
      String quantumName = keyToQuantumName.get(key);
      if(!names().contains(quantumName)) {
        throw new IllegalStateException("the " + name + " spec has no quantum named " + quantumName
          + ", but key " + key + " is bound to it");
      }
    }
  }

}
