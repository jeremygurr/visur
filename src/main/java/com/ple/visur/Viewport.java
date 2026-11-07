package com.ple.visur;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Lays out text as screen rows, so that a renderer never has to.
 *
 * <p>Before this, two things wrapped: the browser, while painting (at its own {@code canvasWidth}), and the
 * server, in {@code EditorModelCoupler.getCanvasEnd()}, to work out what was visible. That made each renderer
 * responsible for agreeing with the other about where a line ends. Now the server lays out once, sends rows with
 * their origins, and a renderer only paints.
 *
 * <p>Pure: takes plain values, returns plain values. It does not read {@link EditorModelCoupler}, the event bus,
 * or any static state, which is what makes the layout rule testable at all.
 *
 * <p>The rule is the browser's: accumulate up to {@code width} characters, and end the row there; a newline ends
 * the row too and is not part of the text. A row is therefore {@code 0..width} characters, never more.
 */
public final class Viewport {

  /** One screen row: {@code text} begins at absolute character index {@code start}. */
  public record Row(int start, String text) {
    public int end() {
      return start + text.length();
    }
  }

  /** Where an absolute character index sits on screen: {@code row} is an index into the rows, not a line. */
  public record Position(int row, int col) {
  }

  private Viewport() {
  }

  /**
   * The rows needed to fill {@code height} screen rows of {@code width} columns, beginning at the absolute
   * character index {@code origin}.
   *
   * <p>Returns fewer than {@code height} rows at the end of the document: those screen rows are blank, and a
   * renderer clears before it paints, so there is nothing to say about them.
   *
   * <p>Slicing from {@code origin} rather than from index 0 is deliberate. The browser's wrap is a pure function
   * of (content, width, where painting starts), so starting mid-line reproduces exactly what the browser drew —
   * including after a resize, when {@code origin} may no longer sit on a row boundary of the new width.
   */
  public static List<Row> rows(String content, int width, int origin, int height) {
    if (content == null || content.isEmpty() || width <= 0 || height <= 0) {
      return List.of();
    }
    int start = Math.max(origin, 0);
    List<Row> rows = new ArrayList<>(Math.min(height, 64));
    while (rows.size() < height && start < content.length()) {
      int end = start;
      while (end < content.length() && end - start < width && content.charAt(end) != '\n') {
        end++;
      }
      rows.add(new Row(start, content.substring(start, end)));
      // A row ends either because it filled up or because it hit a newline, and the two are not the same: a
      // newline that ended a row was consumed by that row, while a newline sitting just past a full row starts
      // the next one. That is why a line of exactly `width` characters is followed by a blank row in the
      // browser, and reproducing it here keeps the two renderers in agreement.
      boolean newlineEndedThisRow = end < content.length()
        && end - start < width
        && content.charAt(end) == '\n';
      start = newlineEndedThisRow ? end + 1 : end;
      // The newline that ends a file opens one more line, empty, and that line is real: nothing is painted on it, but
      // the caret stands on it. A layout without it has nowhere to put a cursor one past the last newline, and the
      // renderer clamps it back onto the line before — which is Enter at the end of a file looking like a paint that
      // missed a beat.
      if (newlineEndedThisRow && start == content.length()) {
        rows.add(new Row(start, ""));
      }
    }
    return Collections.unmodifiableList(rows);
  }

  /**
   * Where {@code absoluteIndex} sits in a laid-out viewport, because a renderer should not have to work it out.
   *
   * <p>An index on a newline reports the end of the row before it, so the cursor rests where the character was.
   * An index outside the layout clamps to the nearest row instead of throwing: the cursor can sit past the last
   * character, and a renderer that crashes because of it would be the one place a bad index takes the screen down.
   */
  public static Position position(List<Row> rows, int absoluteIndex) {
    if (rows.isEmpty()) {
      return new Position(0, 0);
    }
    Position last = new Position(0, 0);
    for (int row = 0; row < rows.size(); row++) {
      Row candidate = rows.get(row);
      if (absoluteIndex < candidate.start()) {
        break;
      }
      // Strictly inside this row's text: that is the answer.
      if (absoluteIndex < candidate.end()) {
        return new Position(row, absoluteIndex - candidate.start());
      }
      // At or past its end. Keep looking, because a wrapped row ends exactly where the next one begins, and
      // the cursor there belongs to the start of the next row (column `width` does not exist). A newline is
      // different: nothing starts at its index, so the fallback below puts the cursor after the last character.
      last = new Position(row, candidate.text().length());
    }
    return last;
  }
}
