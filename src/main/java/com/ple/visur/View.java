package com.ple.visur;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.util.List;

/**
 * The editor state a renderer needs, projected once and serialised once.
 *
 * <p>This replaces a class that was a mutable two-field holder filled in and read back by {@link
 * ModelWasChangedVerticle}. The document itself is not part of the payload: {@link Viewport} lays the visible
 * region out into rows, and the rows carry their own origins, so nothing downstream has to know how wrapping
 * works — which is what makes the payload independent of document size.
 *
 * <p>Everything here is derived, so a change in one place shows up as a change in both {@code render()} and
 * {@link ViewportTest}. Layout is not decided here: it is decided by {@link Viewport}, which this only feeds.
 */
public record View(
  boolean full,
  int origin,
  List<Viewport.Row> rows,
  int span,
  boolean includeSeparators,
  int cursorQuantumStart,
  int cursorQuantumEnd,
  int cursorRow,
  int cursorCol,
  boolean isAtQuantumStart,
  String cursorQuantum,
  String scopeQuantum,
  String editorMode,
  String editorSubmode,
  boolean isInCommandState,
  String commandStateContent,
  int commandCursor,
  String searchTarget,
  int pendingCount,
  String message
) {

  /**
   * Reads the editor model and lays out what is visible.
   *
   * <p>Every message describes the whole viewport, so a renderer that repaints from one is correct whether or not
   * it was told to discard what it had. {@code full} is therefore only an optimisation hint for a renderer that
   * diffs: it says "your cached rows are not worth comparing, start over".
   */
  public static View of(EditorModelCoupler emc) {
    return of(emc, false);
  }

  /**
   * @param full true when the renderer's cached rows cannot be trusted — after the viewport itself changed size,
   *             since every row boundary moved.
   */
  public static View of(EditorModelCoupler emc, boolean full) {
    int origin = emc.session().getCanvasStart();
    int height = Math.max(emc.session().getCanvasHeight(), 0); // declared rows are content rows: no chrome to subtract
    List<Viewport.Row> rows = Viewport.rows(emc.getEditorContent(), emc.session().getCanvasWidth(), origin, height);
    Viewport.Position cursor = Viewport.position(rows, cursorIndex(emc));
    return new View(
      full,
      origin,
      rows,
      emc.getSpan(),
      includeSeparators(emc),
      emc.session().getCursorQuantumStart(),
      emc.session().getCursorQuantumEnd(),
      cursor.row(),
      cursor.col(),
      emc.session().getIsAtQuantumStart(),
      emc.getCursorQuantum().getName(),
      emc.getScopeQuantum().getName(),
      emc.getEditorMode().name(),
      emc.getEditorSubmode().name(),
      emc.getIsInCommandState(),
      emc.getCommandStateContent(),
      emc.getCommandCursor(),
      searchTarget(emc),
      pendingCount(emc),
      emc.getMessage()
    );
  }

  /**
   * Which absolute index the cursor should be drawn at.
   *
   * <p>With a span, the cursor marks the start of the selection. With no span it collapses to one end of the
   * quantum, and which end depends on where the movement arrived from — the browser has always drawn it that
   * way, and a renderer told only {@code cursorRow}/{@code cursorCol} could not work that out for itself.
   */
  private static int cursorIndex(EditorModelCoupler emc) {
    if (emc.getSpan() == 0 && !emc.session().getIsAtQuantumStart()) {
      return emc.session().getCursorQuantumEnd();
    }
    return emc.session().getCursorQuantumStart();
  }

  /** The search prompt, or empty when nothing is being searched: the field is always present on the wire. */
  private static String searchTarget(EditorModelCoupler emc) {
    EditorSubmode submode = emc.getEditorSubmode();
    boolean searching = submode == EditorSubmode.searchForward || submode == EditorSubmode.searchBackward;
    return searching ? emc.getSearchPrompt() : "";
  }

  /**
   * The count typed in front of a key and not yet spent, which the mode line shows while it is pending. Zero is the
   * absence of one: a pending {@code 0} means "none" to every command that reads a count, so it has nothing to show.
   */
  private static boolean includeSeparators(EditorModelCoupler emc) {
    VisurVar state = emc.session().getGlobalVar("includeSeparators");
    return state == null || Boolean.TRUE.equals(state.getVal());
  }

  private static int pendingCount(EditorModelCoupler emc) {
    Integer count = emc.session().getPendingCount();
    return count == null ? 0 : count;
  }

  public JsonObject render() {
    JsonObject payload = new JsonObject();
    payload.put("full", full);
    payload.put("origin", origin);
    payload.put("rows", renderRows());
    payload.put("span", span);
    payload.put("includeSeparators", includeSeparators);
    payload.put("cursorQuantumStart", cursorQuantumStart);
    payload.put("cursorQuantumEnd", cursorQuantumEnd);
    payload.put("cursorRow", cursorRow);
    payload.put("cursorCol", cursorCol);
    payload.put("isAtQuantumStart", isAtQuantumStart);
    payload.put("cursorQuantum", cursorQuantum);
    payload.put("scopeQuantum", scopeQuantum);
    payload.put("editorMode", editorMode);
    payload.put("editorSubmode", editorSubmode);
    payload.put("isInCommandState", isInCommandState);
    payload.put("commandStateContent", commandStateContent);
    payload.put("commandCursor", commandCursor);
    payload.put("searchTarget", searchTarget);
    payload.put("pendingCount", pendingCount);
    payload.put("message", message);
    return payload;
  }

  private JsonArray renderRows() {
    JsonArray rendered = new JsonArray();
    for (Viewport.Row row : rows) {
      rendered.add(new JsonObject().put("start", row.start()).put("text", row.text()));
    }
    return rendered;
  }
}
