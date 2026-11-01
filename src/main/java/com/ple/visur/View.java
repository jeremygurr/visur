package com.ple.visur;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.util.List;

/**
 * The editor state a renderer needs, projected once and serialised once.
 *
 * <p>This replaces a class that was a mutable two-field holder ({@code ca}, {@code editorContent}) filled in and
 * read back by {@link ModelWasChangedVerticle}. The document itself is no longer part of the payload: {@link
 * Viewport} lays the visible region out into rows, and the rows carry their own origins, so nothing downstream
 * has to know how wrapping works.
 *
 * <p>Everything here is derived, so a change in one place shows up as a change in both {@code render()} and
 * {@link ViewportTest}. Layout is not decided here: it is decided by {@link Viewport}, which this only feeds.
 */
public record View(
  boolean full,
  int origin,
  List<Viewport.Row> rows,
  int span,
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
  // Transitional: the browser still paints from these three, so they stay on the wire until the switch ticket
  // removes them together with the browser's own wrapping. Nothing new should read them.
  int ca,
  String editorContent,
  int canvasStart,
  int canvasEnd
) {

  /** The browser's canvas carries one row of chrome, and never content, in its last row. */
  private static final int LAST_CANVAS_ROW_IS_NOT_CONTENT = 1;

  /**
   * Reads the editor model and lays out what is visible.
   *
   * <p>{@code full} is false: every message already describes the whole viewport, so a renderer that repaints
   * from it is correct whether or not it was told to discard what it had. Nothing forces a repaint yet, and the
   * resize path that would own that decision is a later ticket.
   */
  public static View of(EditorModelCoupler emc) {
    int origin = emc.getCanvasStart();
    int height = Math.max(emc.getCanvasHeight() - LAST_CANVAS_ROW_IS_NOT_CONTENT, 0);
    List<Viewport.Row> rows = Viewport.rows(emc.getEditorContent(), emc.getCanvasWidth(), origin, height);
    Viewport.Position cursor = Viewport.position(rows, cursorIndex(emc));
    return new View(
      false,
      origin,
      rows,
      emc.getSpan(),
      emc.getCursorQuantumStart(),
      emc.getCursorQuantumEnd(),
      cursor.row(),
      cursor.col(),
      emc.getIsAtQuantumStart(),
      emc.getCursorQuantum().getName(),
      emc.getScopeQuantum().getName(),
      emc.getEditorMode().name(),
      emc.getEditorSubmode().name(),
      emc.getIsInCommandState(),
      emc.getCommandStateContent(),
      emc.getCommandCursor(),
      searchTarget(emc),
      emc.getCA(),
      emc.getEditorContent(),
      origin,
      emc.getCanvasEnd()
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
    if (emc.getSpan() == 0 && !emc.getIsAtQuantumStart()) {
      return emc.getCursorQuantumEnd();
    }
    return emc.getCursorQuantumStart();
  }

  /** The search prompt, or empty when nothing is being searched: the field is always present on the wire. */
  private static String searchTarget(EditorModelCoupler emc) {
    EditorSubmode submode = emc.getEditorSubmode();
    boolean searching = submode == EditorSubmode.searchForward || submode == EditorSubmode.searchBackward;
    return searching ? emc.getSearchPrompt() : "";
  }

  public JsonObject render() {
    JsonObject payload = new JsonObject();
    payload.put("full", full);
    payload.put("origin", origin);
    payload.put("rows", renderRows());
    payload.put("span", span);
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
    payload.put("ca", ca);
    payload.put("editorContent", editorContent);
    payload.put("canvasStart", canvasStart);
    payload.put("canvasEnd", canvasEnd);
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
