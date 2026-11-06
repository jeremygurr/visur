package com.ple.visur;

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
    pendingCount = null;
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
