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

  public VisurVar getGlobalVar(String varName) {
    return ecs.getGlobalVar(varName, editorModel);
  }

  public VariableMap getGlobalVarMap() {
    return ecs.getGlobalVarMap(editorModel);
  }

  public LineWrapping getLineWrapping() {
    return (LineWrapping) editorModel.get(lineWrapping);
  }

  public int getVirtualCX() {
    return (int)editorModel.get(virtualCX);
  }

  public int getCA() {
    BrickVisurVar caBVV = (BrickVisurVar) getGlobalVar("ca");
    return (int)caBVV.getVal();
  }

  public int getCX() {
    BrickVisurVar cxBVV = (BrickVisurVar) getGlobalVar("cx");
    return (int)cxBVV.getVal();
  }

  public int getCY() {
    BrickVisurVar cyBVV = (BrickVisurVar) getGlobalVar("cy");
    return (int)cyBVV.getVal();
  }

  public ArrayList<Integer> getNextLineIndices() {
    return ecs.getNextLineIndices(editorModel);
  }

  /**
   * The connection the code currently executing is working for.
   *
   * <p>Set once at the top of every inbound message, which is how an operator forty calls deep reaches the geometry
   * of the client whose key it is handling without anybody in the call chain taking a parameter. It is safe because
   * one command runs at a time: every verticle here is deployed once, so its consumers are serialised on one context
   * and a command never yields between adopting a connection and finishing.
   */
  public String getConnection() {
    Object id = editorModel.get(connection);
    return id instanceof String string ? string : Connections.NONE;
  }

  public void putConnection(String id) {
    editorModel.put(connection, id);
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

  public int getCanvasWidth() {
    return session().getCanvasWidth();
  }

  public int getCanvasHeight() {
    return session().getCanvasHeight();
  }

  public int getCanvasStart() {
    return (int)editorModel.get(canvasStart);
  }

  public int getFY() {
    return (int)editorModel.get(fy);
  }

  public int getCanvasEnd() {
    int canvasHeight = getCanvasHeight(); //declared height is content rows already
    int canvasWidth = getCanvasWidth();
    int rowsAdded = 0;
    int canvasEnd = getCanvasStart();
    int fy = getFY();
    ArrayList<Integer> nl = getNextLineIndices();
    while(rowsAdded < canvasHeight && fy < nl.size()) {
      int nextLineStart = nl.get(fy);
      if(canvasEnd + canvasWidth < nextLineStart) {
        canvasEnd += canvasWidth;
      } else {
        canvasEnd = fy < nl.size() - 1 ? nextLineStart - 1 : nextLineStart;
        fy++;
      }
      rowsAdded++;
    }
    return canvasEnd;
  }

  public EditorMode getEditorMode() {
    return (EditorMode)editorModel.get(editorMode);
  }

  public EditorSubmode getEditorSubmode() {
    Stack<EditorSubmode> submodeStack = getEditorSubmodeStack();
    return submodeStack.peek();
  }

  public UndoHistory getUndoHistory() {
    return (UndoHistory) editorModel.get(undoHistory);
  }

  public void putUndoHistory(UndoHistory history) {
    editorModel.put(undoHistory, history);
  }

  public Stack<EditorSubmode> getEditorSubmodeStack() {
    return (Stack<EditorSubmode>) editorModel.get(editorSubmodeStack);
  }

  public boolean getPreviousSearchDirectionWasForward() {
    return (boolean) editorModel.get(previousSearchDirectionWasForward);
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
  public String getSearchPrompt() {
    ExecutionDataStack eds = getExecutionDataStack();
    return eds.size() > 0 ? (String) eds.peek() : "";
  }

  public ExecutionDataStack getExecutionDataStack() {
    return (ExecutionDataStack)editorModel.get(executionData);
  }

  public QuantumNameToQuantum getQuantumNameToQuantum() {
    return (QuantumNameToQuantum)editorModel.get(quantumNameToQuantum);
  }

  public KeyToQuantumName getKeyToQuantumName() {
    return (KeyToQuantumName) editorModel.get(keyToQuantumName);
  }

  public Quantum getCursorQuantum() {
    return (Quantum) getGlobalVar("cursorQuantum").getVal();
  }

  public int getCursorQuantumStart() {
    return (int)editorModel.get(quantumStart);
  }
  public int getCursorQuantumEnd() {
    return (int)editorModel.get(quantumEnd);
  }

  public boolean getIsAtQuantumStart() {
    return (boolean)editorModel.get(isAtQuantumStart);
  }

  public Quantum getScopeQuantum() {
    return (Quantum) getGlobalVar("scopeQuantum").getVal();
  }

  public int getSpan() {
    ObjectVisurVar spanVV = (ObjectVisurVar) getGlobalVar("span");
    return (int)spanVV.getVal();
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

  public void putGlobalVar(String globalVarName, VisurVar globalVarValue) {
    ecs.putGlobalVar(globalVarName, globalVarValue, editorModel);
  }

  public void putLineWrapping(LineWrapping lw) {
    editorModel.put(lineWrapping, lw);
  }

  public void putVirtualCX(int vcx) {
    editorModel.put(virtualCX, vcx);
  }

  public void putCA(int ca) {
    BrickVisurVar caBVV = (BrickVisurVar) getGlobalVar("ca");
    caBVV.putVal(ca);
    putGlobalVar("ca", caBVV);
  }

  public void putCX(int cx) {
    BrickVisurVar cxBVV = (BrickVisurVar) getGlobalVar("cx");
    cxBVV.putVal(cx);
    putGlobalVar("cx", cxBVV);
  }

  public void putCY(int cy) {
    BrickVisurVar cyBVV = (BrickVisurVar) getGlobalVar("cy");
    cyBVV.putVal(cy);
    putGlobalVar("cy", cyBVV);
  }

  public void updateNextLineIndices() {
    ecs.updateNextLineIndices(editorModel);
  }

  /** Resizes the connection in play. Tests use these to say "the window is this wide" without owning a socket. */
  public void putCanvasWidth(int width) {
    Connections.resize(getConnection(), width, getCanvasHeight());
  }

  public void putCanvasHeight(int height) {
    Connections.resize(getConnection(), getCanvasWidth(), height);
  }

  public void putCanvasStart(int cs) {
    editorModel.put(canvasStart, cs);
  }

  public void putFY(int focusY) {
    editorModel.put(fy, focusY);
  }

  public void putEditorMode(EditorMode mode) {
    editorModel.put(editorMode, mode);
  }

  public void putEditorSubmode(EditorSubmode submode) {
    Stack<EditorSubmode> editorSubmodeStack = getEditorSubmodeStack();
    editorSubmodeStack.push(submode);
    putEditorSubmodeStack(editorSubmodeStack);
  }

  public void putEditorSubmodeStack(Stack<EditorSubmode> submodeStack) {
    editorModel.put(editorSubmodeStack, submodeStack);
  }

  public void putPreviousSearchTarget(String pst) {
    ObjectVisurVar ovv = ObjectVisurVar.make(pst);
    putGlobalVar("previousSearchTarget", ovv);
  }

  public void putPreviousSearchDirectionWasForward(boolean prevSearchWasForward) {
    editorModel.put(previousSearchDirectionWasForward, prevSearchWasForward);
  }

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

  public void putOnExecutionDataStack(Object element) {
    ExecutionDataStack eds = (ExecutionDataStack) editorModel.get(executionData);
    eds.push(element);
    putExecutionDataStack(eds);
  }

  public void putExecutionDataStack(ExecutionDataStack stack) {
    editorModel.put(executionData, stack);
  }

  public void putQuantumNameToQuantum(QuantumNameToQuantum qNameToQuantum) {
    editorModel.put(quantumNameToQuantum, qNameToQuantum);
  }

  public void putKeyToQuantumName(KeyToQuantumName keyToQName) {
    editorModel.put(keyToQuantumName, keyToQName);
  }

  public void putCursorQuantum(Quantum cq) {
    ObjectVisurVar cursorQuantumVV = ObjectVisurVar.make(cq);
    putGlobalVar("cursorQuantum", cursorQuantumVV);
  }

  private void putCursorQuantumStart(int startBound) {
    editorModel.put(quantumStart, startBound);
  }

  public void putCursorQuantumStartAndScroll(int startBound) {
    putCursorQuantumStart(startBound);
    while (getCanvasStart() > startBound) {
      decrementCanvasStart();
    }
  }

  void putCursorQuantumEnd(int endBound) {
    editorModel.put(quantumEnd, endBound);
  }

  public void putCursorQuantumEndAndScroll(int endBound) {
    putCursorQuantumEnd(endBound);
    int startBound = getCursorQuantumStart();
    String cursorQuantumName = getCursorQuantum().getName();
    int limit = cursorQuantumName.equals("character") ? startBound : endBound;
    while (getCanvasEnd() < limit) {
      incrementCanvasStart();
    }
  }

  public void putIsAtQuantumStart(boolean isAtQStart) {
    editorModel.put(isAtQuantumStart, isAtQStart);
  }

  public void putScopeQuantum(Quantum q) {
    ObjectVisurVar scopeQuantumVV = ObjectVisurVar.make(q);
    putGlobalVar("scopeQuantum", scopeQuantumVV);
  }

  public void putSpan(int s) {
    ObjectVisurVar spanVV = ObjectVisurVar.make(s);
    putGlobalVar("span", spanVV);
  }

  public void incrementCanvasStart() {
    int canvasStart = getCanvasStart();
    int canvasWidth = getCanvasWidth();
    int fy = getFY();
    ArrayList<Integer> nl = getNextLineIndices();
    int nextLineStart = nl.get(fy);
    if(canvasStart + canvasWidth < nextLineStart) {
      canvasStart += canvasWidth;
    } else {
      canvasStart = nextLineStart;
      putFY(++fy);
    }
    putCanvasStart(canvasStart);
  }

  public void decrementCanvasStart() {
    int canvasStart = getCanvasStart();
    ArrayList<Integer> nl = getNextLineIndices();
    int fy = getFY();
    fy = Math.min(fy, nl.size() - 1);
    int currentLineStart = fy > 0 ? nl.get(fy - 1) : 0;
    if(nl.get(fy) >= canvasStart) {
      int canvasWidth = getCanvasWidth();
      if (canvasStart > currentLineStart) {
        canvasStart -= canvasWidth;
      } else {
        putFY(--fy);
        int prevLineStart = fy > 0 ? nl.get(fy - 1) : 0;
        int prevLineLength = currentLineStart - prevLineStart - 1;
        if (prevLineLength - 1 <= canvasWidth) {
          canvasStart = prevLineStart;
        } else {
          canvasStart = currentLineStart - (prevLineLength % canvasWidth) - 1;
        }
      }
      putCanvasStart(canvasStart);
    } else {
      putCanvasStart(currentLineStart);
      putFY(fy);
    }
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
