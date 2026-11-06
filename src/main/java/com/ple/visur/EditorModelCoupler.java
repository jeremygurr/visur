package com.ple.visur;

import CursorPositionDC.EditorContentService;
import io.vertx.rxjava3.core.shareddata.LocalMap;
import io.vertx.rxjava3.core.shareddata.SharedData;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Stack;

import static com.ple.visur.EditorModelKey.*;

public class EditorModelCoupler {

  LocalMap<EditorModelKey, Object> editorModel;
  // Simple time-based debounce for swap file writes: only write if last write was > 500ms ago.
  // Chosen because writing a few KB to a local filesystem takes microseconds; 500ms is cheap
  // enough to avoid noticeable keystroke latency while keeping the swap fresh.
  private volatile long lastSwapWriteTime = 0;
  private static final long SWAP_DEBOUNCE_MS = 500;
  private EditorContentService ecs = ServiceHolder.editorContentService;

  private EditorModelCoupler(SharedData sharedData) {
    this.editorModel = sharedData.getLocalMap("editorModel");
  }

  public static EditorModelCoupler make(SharedData sharedData) {
    return new EditorModelCoupler(sharedData);
  }

  public String getStrToMatch(boolean searchForwards, int startingPoint) {
    String editorContent = getEditorContent();
    String strToMatch = "";
    if(searchForwards && startingPoint < editorContent.length()) {
      strToMatch = editorContent.substring(startingPoint, startingPoint + 1);
    } else if(startingPoint > 0) {
      strToMatch = editorContent.substring(startingPoint - 1, startingPoint);
    }
    return strToMatch;
  }

  public String getEditorContent() {
    return ecs.getEditorContent(editorModel);
  }

  public ArrayList<Integer> getNextLineIndices() {
    return ecs.getNextLineIndices(editorModel);
  }

  /** One cell for the whole document; see {@link CursorPositionDC.EditorContentService#putNewlineIndicesCell}. */
  public void putNewlineIndicesCell(BrickVisurVar cell) {
    ecs.putNewlineIndicesCell(cell, editorModel);
  }

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

  public String getConnection() {
    String id = connectionForThisThread.get();
    return id == null ? Connections.NONE : id;
  }

  public void putConnection(String id) {
    connectionForThisThread.set(id);
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

  /** The cursor of the session in play. See {@link #session()}. */
  public int getCA() {
    return session().getCA();
  }

  public void putCA(int ca) {
    session().putCA(ca);
  }

  public int getCX() {
    return session().getCX();
  }

  public void putCX(int cx) {
    session().putCX(cx);
  }

  public int getCY() {
    return session().getCY();
  }

  public void putCY(int cy) {
    session().putCY(cy);
  }

  public int getSpan() {
    return session().getSpan();
  }

  public void putSpan(int span) {
    session().putSpan(span);
  }

  public Quantum getCursorQuantum() {
    return (Quantum) session().getGlobalVar("cursorQuantum").getVal();
  }

  public void putCursorQuantum(Quantum cursorQuantum) {
    session().putGlobalVar("cursorQuantum", ObjectVisurVar.make(cursorQuantum));
  }

  public Quantum getScopeQuantum() {
    return (Quantum) session().getGlobalVar("scopeQuantum").getVal();
  }

  public void putScopeQuantum(Quantum scopeQuantum) {
    session().putGlobalVar("scopeQuantum", ObjectVisurVar.make(scopeQuantum));
  }

  public void putPreviousSearchTarget(String previousSearchTarget) {
    session().putGlobalVar("previousSearchTarget", ObjectVisurVar.make(previousSearchTarget));
  }

  public UndoHistory getUndoHistory() {
    return (UndoHistory) editorModel.get(undoHistory);
  }

  public void putUndoHistory(UndoHistory history) {
    editorModel.put(undoHistory, history);
  }

  public KeymapMap getKeymapMap() {
    return (KeymapMap) editorModel.get(modeToKeymap);
  }

  public boolean getIsInCommandState() {
    return (boolean)editorModel.get(isInCommandState);
  }

  public String getCommandStateContent() {
    return (String)editorModel.get(commandStateContent);
  }

  public int getCommandCursor() {
    return (int)editorModel.get(commandCursor);
  }

  /**
   * The text currently being searched for, or "" when nothing is.
   *
   * <p>The cursor that marks the end of the prompt used to be appended where the payload was assembled
   * ({@code searchTarget += "_"}), which put a rendering decision in the serialiser and left one renderer able to
   * show a caret the other could not. The caret belongs to the renderer now.
   */
  public QuantumNameToQuantum getQuantumNameToQuantum() {
    return (QuantumNameToQuantum)editorModel.get(quantumNameToQuantum);
  }

  public KeyToQuantumName getKeyToQuantumName() {
    return (KeyToQuantumName) editorModel.get(keyToQuantumName);
  }

  public void initializeEditorContent(String content) {
    ecs.initializeEditorContent(content, editorModel);
  }

  public void putEditorContent(String content) {
    ecs.putEditorContent(content, editorModel);
    writeSwapFileDebounced(content);
  }

  /**
   * Writes the swap file for the current source path, debounced by 500ms.
   *
   * <p>Chosen hook: every call to {@code putEditorContent}. This covers all content changes
   * (startup load, save, loadFile, edits) in a single place. The 500ms debounce prevents
   * excessive I/O on rapid edits while keeping the swap reasonably fresh.
   */
  private void writeSwapFileDebounced(String content) {
    String sourcePath = getSourceFilePath();
    if (sourcePath == null || sourcePath.isEmpty()) {
      return;
    }
    long now = System.currentTimeMillis();
    if (now - lastSwapWriteTime < SWAP_DEBOUNCE_MS) {
      return; // debounce: not enough time since last write
    }
    lastSwapWriteTime = now;
    try {
      Path swapPath = Paths.get(sourcePath + ".visur-swap");
      Files.write(swapPath, content.getBytes(StandardCharsets.UTF_8),
        StandardOpenOption.WRITE, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    } catch (IOException e) {
      // Non-fatal: if swap write fails, the document is still correct.
      System.out.println("Swap write failed: " + e.getMessage());
    }
  }

  public void updateNextLineIndices() {
    ecs.updateNextLineIndices(editorModel);
  }

  /** Resizes the connection in play. Tests use these to say "the window is this wide" without owning a socket. */
  public void putKeymapMap(KeymapMap keymapMap) {
    editorModel.put(modeToKeymap, keymapMap);
  }

  public void putIsInCommandState(boolean inCommandState) {
    editorModel.put(isInCommandState, inCommandState);
  }

  public void putCommandStateContent(String content) {
    editorModel.put(commandStateContent, content);
  }

  public void putCommandCursor(int x) {
    editorModel.put(commandCursor, x);
  }

  public void putQuantumNameToQuantum(QuantumNameToQuantum qNameToQuantum) {
    editorModel.put(quantumNameToQuantum, qNameToQuantum);
  }

  public void putKeyToQuantumName(KeyToQuantumName keyToQName) {
    editorModel.put(keyToQuantumName, keyToQName);
  }

  /**
   * Tell the user something went wrong: on the screen, and in the log.
   *
   * <p>The screen half is what makes an error an error — before it, a failed load and an unbound key were
   * indistinguishable to anyone not reading the server log. The log half stays because a message on screen is gone on
   * the next keypress and a bug report outlives it.
   */
  public void reportError(String message) {
    System.out.println(message);
    editorModel.put(EditorModelKey.message, message);
  }

  /** The message to draw, or empty when there is none: the field is always present on the wire. */
  public String getMessage() {
    Object val = editorModel.get(message);
    return val == null ? "" : (String) val;
  }

  public void clearMessage() {
    editorModel.remove(message);
  }

  public String getSourceFilePath() {
    Object val = editorModel.get(sourceFilePath);
    return val == null ? null : (String) val;
  }

  public void setSourceFilePath(String path) {
    if (path == null) {
      editorModel.remove(sourceFilePath);
    } else {
      editorModel.put(sourceFilePath, path);
    }
  }

}
