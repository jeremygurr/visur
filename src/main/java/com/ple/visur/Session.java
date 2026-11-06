package com.ple.visur;

import CursorPositionDC.CursorPositionDCHolder;
import DataClass.CompoundDataClassBrick;
import DataClass.LayeredDataClassBrick;
import DataClass.PrimitiveDataClassBrick;

import java.util.ArrayList;
import java.util.HashMap;

/**
 * A pair of hands: where the cursor is, what is selected, what was typed last.
 *
 * <p>Everything here survives closing the text and dies when the window closes. The document on screen is somebody
 * else's state — two sessions can be looking at one document, and then a cursor, a selection or a typed count is
 * whichever window's hands made it. That is the whole reason this class exists: with these fields in the shared model,
 * a second client's typing continued the first client's work, digit by digit.
 *
 * <p>One instance per open connection, held by {@link Connections}, which is what {@link EditorModelCoupler#session()}
 * resolves for the connection currently executing. There is deliberately no {@code current()}: a session is obtained by
 * saying whose it is.
 */
public class Session {

  /** What a client gets before it has said how big its window is: the terminal every terminal pretends to be. */
  private static final Connections.Viewport UNTOLD = new Connections.Viewport(80, 24);

  private Connections.Viewport viewport = UNTOLD;

  /**
   * The window's named variables — the ones the keymap grammar shuffles by name.
   *
   * <p>Commands are written as strings, and a string names a variable: {@code "span" "tempSpan" ->} stashes the
   * selection so `o` can select one quantum, and `g` puts it back. So a window's variables have to be a window's
   * map, or one window's `o` rearranges another's. They used to be one map for the process, and that is exactly how
   * `3 g` on one client started failing to find what it had stashed.
   */
  private VariableMap vars;

  /**
   * Where the cursor is: its index in the content, its row, its column. They are one fact stated three ways, and the
   * data classes underneath keep the three consistent — move to a row and the column and the index follow — so they
   * are one object per window rather than three fields somebody remembers to update.
   *
   * <p>{@code nl}, the offset of every line in the document, is handed in from outside because it is the document's
   * and not the window's: it is the one input the cursor derives from that every window shares.
   */
  /** Where every line in the document starts, shared with every other window on this document. */
  private PrimitiveDataClassBrick lineOffsets;

  private BrickVisurVar ca;
  private BrickVisurVar cx;
  private BrickVisurVar cy;

  /**
   * The count typed in front of a key ({@code 12 s}), or null when none is pending. Absence is a state of its own, so
   * that a count of zero can mean zero rather than "no count".
   */
  private Integer pendingCount;

  Session() {
    openFresh();
  }

  /**
   * A window with nothing typed into it.
   *
   * <p>The constructor calls it, so a socket that arrives mid-session starts where a new window starts, and
   * {@link InitializerService} calls it for the session that exists before anybody has connected — which is also how
   * every test that re-initialises the model gets a window that is not still counting the last test's keystrokes.
   */
  public void openFresh() {
    vars = new VariableMap(new HashMap<>());
    vars.put("span", ObjectVisurVar.make(0));
    pendingCount = null;
  }

  /**
   * Give this window its own cursor, wired to the line offsets it shares with every other window looking at this
   * document.
   *
   * <p>Each window needs its own cells: they are mutable and they are where the cursor lives, so two windows sharing
   * them is two windows sharing one cursor, which is the bug this split exists to end. The line offsets go the other
   * way — one cell for the document — because editing the text moves everybody's cursor the same way, and the data
   * classes invalidate through the shared cell when that happens.
   */
  void installCursorCells(PrimitiveDataClassBrick nlDCB) {
    lineOffsets = nlDCB;
    CursorPositionDCHolder cells = new CursorPositionDCHolder();
    LayeredDataClassBrick cursorPositionDCB = cells.cursorPositionDC.makeBrick(nlDCB);
    CompoundDataClassBrick coordinatesDCB = cursorPositionDCB.getLayer(0);
    CompoundDataClassBrick caAndNLDCB = (CompoundDataClassBrick) coordinatesDCB.getInner("caAndNL");
    CompoundDataClassBrick cxcyAndNLDCB = (CompoundDataClassBrick) coordinatesDCB.getInner("cxcyAndNL");
    PrimitiveDataClassBrick caDCB = (PrimitiveDataClassBrick) caAndNLDCB.getInner("ca");
    PrimitiveDataClassBrick cxDCB = (PrimitiveDataClassBrick) cxcyAndNLDCB.getInner("cx");
    PrimitiveDataClassBrick cyDCB = (PrimitiveDataClassBrick) cxcyAndNLDCB.getInner("cy");
    caAndNLDCB.putInner("ca", caDCB);
    caAndNLDCB.putInner("nl", nlDCB);
    cxcyAndNLDCB.putInner("cx", cxDCB);
    cxcyAndNLDCB.putInner("cy", cyDCB);
    cxcyAndNLDCB.putInner("nl", nlDCB);
    coordinatesDCB.putInner("caAndNL", caAndNLDCB);
    coordinatesDCB.putInner("cxcyAndNL", cxcyAndNLDCB);
    ca = BrickVisurVar.make(caDCB);
    cx = BrickVisurVar.make(cxDCB);
    cy = BrickVisurVar.make(cyDCB);
    putCA(0);
  }

  public VisurVar getGlobalVar(String varName) {
    return vars.get(varName);
  }

  public void putGlobalVar(String varName, VisurVar var) {
    vars.put(varName, var);
  }

  public VariableMap getGlobalVarMap() {
    return vars;
  }

  public int getCA() {
    return (int) ca.getVal();
  }

  public void putCA(int contentAddress) {
    ca.putVal(contentAddress);
  }

  public int getCX() {
    return (int) cx.getVal();
  }

  public void putCX(int column) {
    cx.putVal(column);
  }

  public int getCY() {
    return (int) cy.getVal();
  }

  public void putCY(int row) {
    cy.putVal(row);
  }

  public void putCursorQuantum(Quantum cursorQuantum) {
    putGlobalVar("cursorQuantum", ObjectVisurVar.make(cursorQuantum));
  }

  public void putScopeQuantum(Quantum scopeQuantum) {
    putGlobalVar("scopeQuantum", ObjectVisurVar.make(scopeQuantum));
  }

  /**
   * The rows this window is showing, in characters of content: the first index past the last one drawn.
   *
   * <p>Derived, not stored, because the moment it is stored it is a second answer to "what is on screen" — and the
   * window can be scrolled while the cursor is nowhere near either end of it.
   */
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

  /** The window scrolls a screenful right, one wrapped row at a time as its left edge crosses line ends. */
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

  public ArrayList<Integer> getNextLineIndices() {
    return (ArrayList<Integer>) lineOffsets.getVal();
  }

  public int getCanvasStart() {
    return canvasStart;
  }

  public void putCanvasStart(int start) {
    canvasStart = start;
  }

  public int getFY() {
    return fy;
  }

  public void putFY(int focusY) {
    fy = focusY;
  }

  /**
   * The column the cursor wants back when it moves vertically, which is not necessarily the one it is in: a cursor
   * that was at column 40 and moves down onto a short line is at column 4, and moving up again should return it to
   * 40. That wish is this window's, because the other window never asked for it.
   */
  public int getVirtualCX() {
    return virtualCX;
  }

  public void putVirtualCX(int virtualColumn) {
    virtualCX = virtualColumn;
  }

  public int getCursorQuantumStart() {
    return quantumStart;
  }

  public void putCursorQuantumStart(int start) {
    quantumStart = start;
  }

  /** Move the start of the range and bring the window with it, so a cursor can never be off screen. */
  public void putCursorQuantumStartAndScroll(int start) {
    putCursorQuantumStart(start);
    while (getCanvasStart() > start) {
      decrementCanvasStart();
    }
  }

  public int getCursorQuantumEnd() {
    return quantumEnd;
  }

  public void putCursorQuantumEnd(int end) {
    quantumEnd = end;
  }

  public void putCursorQuantumEndAndScroll(int end) {
    putCursorQuantumEnd(end);
    int start = getCursorQuantumStart();
    String cursorQuantumName = ((Quantum) getGlobalVar("cursorQuantum").getVal()).getName();
    int limit = cursorQuantumName.equals("character") ? start : end;
    while (getCanvasEnd() < limit) {
      incrementCanvasStart();
    }
  }

  public boolean getIsAtQuantumStart() {
    return isAtQuantumStart;
  }

  public void putIsAtQuantumStart(boolean atQuantumStart) {
    isAtQuantumStart = atQuantumStart;
  }

  public Quantum getCursorQuantum() {
    return (Quantum) getGlobalVar("cursorQuantum").getVal();
  }

  public int getSpan() {
    return (int) ((ObjectVisurVar) vars.get("span")).getVal();
  }

  public void putSpan(int s) {
    vars.put("span", ObjectVisurVar.make(s));
  }

  private int quantumStart;
  private int quantumEnd;
  private boolean isAtQuantumStart;
  private int virtualCX;
  private int canvasStart;
  private int fy;

  public int getCanvasWidth() {
    return viewport.cols();
  }

  public int getCanvasHeight() {
    return viewport.rows();
  }

  /** The grid this client said it can paint. Rows are content rows: the client subtracts its own chrome. */
  public void resize(int cols, int rows) {
    viewport = new Connections.Viewport(cols, rows);
  }

  /** One dimension at a time, because a test says "the window is this wide" and means the height stays. */
  public void putCanvasWidth(int cols) {
    resize(cols, getCanvasHeight());
  }

  public void putCanvasHeight(int rows) {
    resize(getCanvasWidth(), rows);
  }

  public Integer getPendingCount() {
    return pendingCount;
  }

  public void putPendingCount(int count) {
    pendingCount = count;
  }

  public void clearPendingCount() {
    pendingCount = null;
  }
}
