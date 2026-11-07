package com.ple.visur;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The terminal client and the browser must agree on where a line ends. Before this class the browser decided
 * that for itself (wrapping at {@code canvasWidth} while it painted) and the server decided it again in
 * {@code getCanvasEnd}. These tests pin the browser's rule, which is the one both renderers have to adopt.
 */
class ViewportTest {

  private static final String ALPHA = "abcdefghijklmnopqrstuvwxyz";

  @Test
  void aLineShorterThanTheWidthIsOneRow() {
    List<Viewport.Row> rows = Viewport.rows("abc\ndef", 10, 0, 5);

    assertEquals(List.of(new Viewport.Row(0, "abc"), new Viewport.Row(4, "def")), rows);
  }

  @Test
  void aFullWidthLineFollowedByANewlineSpendsARowOnTheNewline() {
    // The browser advances y when the newline is drawn, so the newline gets a screen row of its own and the
    // next line starts one row lower than the naive reading would expect.
    List<Viewport.Row> rows = Viewport.rows("abcdefghij\nx", 10, 0, 5);

    assertEquals(List.of(
      new Viewport.Row(0, "abcdefghij"),
      new Viewport.Row(10, ""),
      new Viewport.Row(11, "x")
    ), rows);
  }

  @Test
  void aLineLongerThanTheWidthWrapsEveryWidthCharacters() {
    List<Viewport.Row> rows = Viewport.rows(ALPHA, 10, 0, 5);

    assertEquals(List.of(
      new Viewport.Row(0, "abcdefghij"),
      new Viewport.Row(10, "klmnopqrst"),
      new Viewport.Row(20, "uvwxyz")
    ), rows);
  }

  @Test
  void stopsAtTheRequestedHeightEvenWithMoreContent() {
    assertEquals(List.of(
      new Viewport.Row(0, "abcdefghij"),
      new Viewport.Row(10, "klmnopqrst")
    ), Viewport.rows(ALPHA, 10, 0, 2));
  }

  @Test
  void startsAtAnOriginThatIsNotARowBoundary() {
    // After a resize, canvasStart can stop being a multiple of the new width. The browser starts painting
    // there all the same, so the rows must be laid out from there too.
    assertEquals(List.of(
      new Viewport.Row(5, "fghijklmno"),
      new Viewport.Row(15, "pqrstuvwxy"),
      new Viewport.Row(25, "z")
    ), Viewport.rows(ALPHA, 10, 5, 5));
  }

  /**
   * The newline that ends a file opens a line, and the line is in the layout even though it has nothing to paint.
   *
   * <p>It costs a row of blanks, which is what a renderer clears anyway. What it buys is the only place a cursor one
   * past the last newline can go: without it the caret for an empty last line clamps back onto the line before, which
   * is Enter at the end of a file looking like a paint that missed a beat (see InsertNewlineTest).
   */
  @Test
  void aFileEndingInANewlineHasItsEmptyLastRow() {
    assertEquals(List.of(new Viewport.Row(0, "ab"), new Viewport.Row(3, "")), Viewport.rows("ab\n", 10, 0, 5));
  }

  /** Two final newlines are two empty lines, and only the last one needs the special case. */
  @Test
  void everyEmptyLineAtTheEndOfAFileIsARow() {
    assertEquals(List.of(
      new Viewport.Row(0, "ab"), new Viewport.Row(3, ""), new Viewport.Row(4, "")),
      Viewport.rows("ab\n\n", 10, 0, 5));
    assertEquals(List.of(new Viewport.Row(0, "ab")), Viewport.rows("ab", 10, 0, 5));
  }

  /** The caret on that row is its start, not the end of the row above. */
  @Test
  void positionOnePastATrailingNewlineIsTheEmptyRowAfterIt() {
    List<Viewport.Row> rows = Viewport.rows("ab\n", 10, 0, 5);

    assertEquals(new Viewport.Position(1, 0), Viewport.position(rows, 3));
    assertEquals(new Viewport.Position(0, 2), Viewport.position(rows, 2), "an index on the newline itself still rests after the last character");
  }

  @Test
  void emptyOrUnusableViewportsHaveNoRows() {
    assertEquals(List.of(), Viewport.rows("", 10, 0, 5));
    assertEquals(List.of(), Viewport.rows(null, 10, 0, 5));
    assertEquals(List.of(), Viewport.rows("abc", 0, 0, 5));
    assertEquals(List.of(), Viewport.rows("abc", 10, 0, 0));
    assertEquals(List.of(), Viewport.rows("abc", 10, 26, 5));
  }

  @Test
  void positionOfAnIndexInsideARow() {
    List<Viewport.Row> rows = Viewport.rows("abc\ndef", 10, 0, 5);

    assertEquals(new Viewport.Position(0, 0), Viewport.position(rows, 0));
    assertEquals(new Viewport.Position(0, 2), Viewport.position(rows, 2));
    assertEquals(new Viewport.Position(1, 0), Viewport.position(rows, 4));
  }

  @Test
  void positionOfANewlineIsTheEndOfItsRow() {
    List<Viewport.Row> rows = Viewport.rows("abc\ndef", 10, 0, 5);

    assertEquals(new Viewport.Position(0, 3), Viewport.position(rows, 3));
  }

  @Test
  void positionOfAWrappedBoundaryIsTheStartOfTheNextRow() {
    List<Viewport.Row> rows = Viewport.rows(ALPHA, 10, 0, 5);

    assertEquals(new Viewport.Position(0, 9), Viewport.position(rows, 9));
    assertEquals(new Viewport.Position(1, 0), Viewport.position(rows, 10));
    assertEquals(new Viewport.Position(2, 5), Viewport.position(rows, 25));
  }

  @Test
  void positionClampsRatherThanIndexingOutOfBounds() {
    List<Viewport.Row> rows = Viewport.rows("abc\ndef", 10, 0, 5);

    assertEquals(new Viewport.Position(1, 3), Viewport.position(rows, 99));
    assertEquals(new Viewport.Position(0, 0), Viewport.position(List.of(), 3));
    assertEquals(new Viewport.Position(0, 0), Viewport.position(Viewport.rows("abc\ndef", 10, 4, 5), 0));
  }
}
