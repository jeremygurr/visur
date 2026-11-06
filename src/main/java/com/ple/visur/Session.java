package com.ple.visur;

import CursorPositionDC.CursorPositionDCHolder;
import DataClass.CompoundDataClassBrick;
import DataClass.LayeredDataClassBrick;
import DataClass.PrimitiveDataClassBrick;

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

  public int getSpan() {
    return (int) ((ObjectVisurVar) vars.get("span")).getVal();
  }

  public void putSpan(int s) {
    vars.put("span", ObjectVisurVar.make(s));
  }

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
