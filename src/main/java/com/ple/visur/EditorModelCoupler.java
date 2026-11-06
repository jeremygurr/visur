package com.ple.visur;

/**
 * The locator for the two owners of editor state, and the configuration neither of them owns.
 *
 * <p>{@link #document()} is the text and what it costs to know it; {@link #session()} is the pair of hands currently
 * at the keyboard. Nothing here holds state that belongs to either, which is the entire point: while cursor and
 * content lived in one map, a second client's keystroke continued the first client's work, and there was no way to ask
 * a piece of state whose it was. The two owners answer that question, and the type that answers it is where a field
 * lives.
 *
 * <p>What is left is built once at boot and read from then on — the keymaps and the quantum registries — plus the
 * adopted connection, which is the argument {@link #session()} needs and belongs to neither owner.
 */
public class EditorModelCoupler {

  /**
   * The connection the code currently executing is working for, and it is confined to this thread on purpose.
   *
   * <p>Set once at the top of every inbound message, which is how an operator forty calls deep reaches the geometry
   * of the client whose key it is handling without anybody in the call chain taking a parameter. It holds because a
   * command never yields: a verticle's consumers are serialised on one context, and the handler runs to completion on
   * one event-loop thread.
   *
   * <p>It would not hold if it were shared. Repainting is a verticle of its own, on another context, and it adopts
   * each connection in turn to lay the document out at that client's width — so a repaint that started for another
   * client while a key was mid-command used to leave the rest of that command reading the other client's session,
   * which showed up as {@code 5 s} setting a span of one because the count typed a key earlier had gone missing.
   * Per-thread, the repaint steals nothing, because it is not on the key's thread.
   */
  private static final ThreadLocal<String> connectionForThisThread = new ThreadLocal<>();

  private final Document document = new Document();

  private KeymapMap keymapMap;
  private QuantumNameToQuantum quantumNameToQuantum;
  private KeyToQuantumName keyToQuantumName;

  public static EditorModelCoupler make() {
    return new EditorModelCoupler();
  }

  /**
   * The hands currently at this keyboard: the session of {@link #getConnection()}.
   *
   * <p>Cursor, selection, typed count, submode, scroll position — everything a window owns rather than a document.
   * Asking is the only way to get one: there is no current session to reach for, which is how a second client ended up
   * typing into the first client's count.
   */
  public Session session() {
    return Connections.session(getConnection());
  }

  /**
   * The text on screen. One document per process until the screens map makes it a question with an argument; the
   * reason it is already a method with no parameters to spare is that adding the argument is then nothing else.
   */
  public Document document() {
    return document;
  }

  public String getConnection() {
    String id = connectionForThisThread.get();
    return id == null ? Connections.NONE : id;
  }

  public void putConnection(String id) {
    connectionForThisThread.set(id);
  }

  public KeymapMap getKeymapMap() {
    return keymapMap;
  }

  public void putKeymapMap(KeymapMap keymapMap) {
    this.keymapMap = keymapMap;
  }

  public QuantumNameToQuantum getQuantumNameToQuantum() {
    return quantumNameToQuantum;
  }

  public void putQuantumNameToQuantum(QuantumNameToQuantum qNameToQuantum) {
    quantumNameToQuantum = qNameToQuantum;
  }

  public KeyToQuantumName getKeyToQuantumName() {
    return keyToQuantumName;
  }

  public void putKeyToQuantumName(KeyToQuantumName keyToQName) {
    keyToQuantumName = keyToQName;
  }
}
