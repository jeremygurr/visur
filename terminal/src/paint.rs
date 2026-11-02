// Component 6 of the design: the grid becomes escape bytes. One `String` per frame, one `write(2)` by the
// caller — never `std::io::Stdout`, whose line buffering would split a frame.
//
// Ticket 06 adds the damage path (`diff`); this is `full_repaint`, which every repaint of the skeleton uses.

use crate::screen::{Cell, Grid};

fn cursor_shape(block: bool) -> &'static str {
  // DECSCUSR: 1 is a blinking block, 5 a blinking bar.
  if block { "\x1b[1 q" } else { "\x1b[5 q" }
}

/// How many cells of a row are worth painting: everything up to the last one that is not a plain blank.
fn painted_width(grid: &Grid, index: usize) -> usize {
  (0..grid.cols)
    .rposition(|col| {
      let cell = grid.cell(index, col);
      cell.ch != ' ' || cell.inverse
    })
    .map(|col| col + 1)
    .unwrap_or(0)
}

/// Cells from `from` to `to`, inverting only where the attribute changes. `inverse` is the state the terminal is in, and
/// it is carried across runs so a row that starts inverted costs one `SGR`, not one per cell.
fn cells(grid: &Grid, index: usize, from: usize, to: usize, inverse: &mut bool, out: &mut String) {
  for col in from..to {
    let Cell { ch, inverse: wanted } = grid.cell(index, col);
    if wanted != *inverse {
      out.push_str(if wanted { "\x1b[7m" } else { "\x1b[0m" });
      *inverse = wanted;
    }
    out.push(ch);
  }
}

fn row(grid: &Grid, index: usize) -> String {
  let width = painted_width(grid, index);
  let mut out = format!("\x1b[{};1H", index + 1);
  let mut inverse = false;
  cells(grid, index, 0, width, &mut inverse, &mut out);
  if inverse {
    out.push_str("\x1b[0m"); // reset before erasing, or the tail is erased in reverse video
  }
  if width < grid.cols {
    out.push_str("\x1b[K"); // one erase instead of a row of blanks
  }
  out
}

/// What changed between two frames of the same geometry, then the cursor.
///
/// Damage travels as row runs, never rectangles (spec §5.3): a row that did not change emits nothing at all, so the
/// bytes of a frame follow what was typed rather than the size of the screen.
pub fn diff(prev: &Grid, next: &Grid) -> String {
  debug_assert_eq!((prev.cols, prev.rows), (next.cols, next.rows), "a geometry change is a full repaint");
  let mut out = String::new();
  let mut inverse = false;
  for index in 0..next.rows {
    let Some((first, last)) = changed(prev, next, index) else { continue };
    // A blank the row no longer reaches is the eraser's job, so the run stops at the new end instead of writing
    // blanks that are about to be erased.
    let (width, was) = (painted_width(next, index), painted_width(prev, index));
    let to = (last + 1).min(width.max(first));
    out.push_str(&format!("\x1b[{};{}H", index + 1, first + 1));
    cells(next, index, first, to, &mut inverse, &mut out);
    // Only when the row now ends at or before where this run stopped: erasing past that would wipe text that is
    // still on the screen.
    let erased = to >= width && was > to;
    if inverse {
      out.push_str("\x1b[0m"); // reset before erasing, or the tail is erased in reverse video
      inverse = false;
    }
    if erased {
      out.push_str("\x1b[K"); // the row is shorter than it was: one erase instead of a run of blanks
    }
  }
  out.push_str(&cursor(next));
  out
}

/// The columns of one row that differ, first and last inclusive. `None` means nothing on this row is worth sending.
fn changed(prev: &Grid, next: &Grid, index: usize) -> Option<(usize, usize)> {
  let differs = |col: usize| prev.cell(index, col) != next.cell(index, col);
  let first = (0..next.cols).find(|col| differs(*col))?;
  let last = (0..next.cols).rev().find(|col| differs(*col)).expect("first found one");
  Some((first, last))
}

fn cursor(grid: &Grid) -> String {
  let mut out = String::from("\x1b[?25h");
  out.push_str(cursor_shape(grid.cursor_block));
  out.push_str(&format!("\x1b[{};{}H", grid.cursor.0 + 1, grid.cursor.1 + 1));
  out
}

/// The frame that has to be sent: a diff against the last one, or a full repaint.
///
/// Cached rows are reused only when they describe the same grid — so `full`, the start of a session and a geometry
/// change all discard them, and nothing else does.
pub fn frame(prev: Option<&Grid>, next: &Grid, full: bool) -> String {
  match prev {
    Some(prev) if !full && prev.cols == next.cols && prev.rows == next.rows => diff(prev, next),
    _ => self::full(next),
  }
}

/// A complete repaint of every row, then the cursor.
pub fn full(grid: &Grid) -> String {
  let mut out = String::with_capacity(grid.cols * grid.rows + 256);
  for index in 0..grid.rows {
    out.push_str(&row(grid, index));
  }
  out.push_str(&cursor(grid));
  out
}

#[cfg(test)]
mod tests {
  use super::*;
  use crate::protocol::{Row, ViewUpdate};
  use crate::screen::compose;

  fn styled() -> ViewUpdate {
    // "abcd" with the span covering b and c, so the row needs one attribute switch each way.
    let mut view = ViewUpdate {
      rows: vec![Row { start: 0, text: "abcd".to_string() }],
      cursor_quantum_start: 1,
      cursor_quantum_end: 3,
      span: 1,
      cursor_row: 0,
      cursor_col: 1,
      editor_mode: "navigate".to_string(),
      editor_submode: "navigate".to_string(),
      ..ViewUpdate::default()
    };
    view.quantum_for_test();
    view
  }

  trait Quantum {
    fn quantum_for_test(&mut self);
  }

  impl Quantum for ViewUpdate {
    fn quantum_for_test(&mut self) {
      self.cursor_quantum = "character".to_string();
    }
  }

  #[test]
  fn a_frame_is_cup_per_row_sgr_on_change_and_one_cursor_at_the_end() {
    let grid = compose(&styled(), 6, 2);
    assert_eq!(
      full(&grid),
      concat!(
        "\x1b[1;1H", "a", "\x1b[7m", "bc", "\x1b[0m", "d", "\x1b[K",
        // The mode line fills the row exactly, so it needs no erase after it.
        "\x1b[2;1H", "\x1b[7m", " NAVIG", "\x1b[0m",
        "\x1b[?25h", "\x1b[1 q", "\x1b[1;2H",
      )
    );
  }

  #[test]
  fn a_zero_span_asks_for_a_bar_cursor_and_a_one_span_for_a_block() {
    let mut view = styled();
    assert!(full(&compose(&view, 6, 2)).contains("\x1b[1 q"), "block for span 1");
    view.span = 0;
    assert!(full(&compose(&view, 6, 2)).contains("\x1b[5 q"), "bar for span 0");
  }

  #[test]
  fn nothing_at_all_is_painted_when_nothing_changed() {
    let grid = compose(&styled(), 40, 3);
    assert_eq!(
      diff(&grid, &grid.clone()),
      concat!("\x1b[?25h", "\x1b[1 q", "\x1b[1;2H"),
      "an unchanged screen is only the cursor: no CUP, no SGR, no EL"
    );
  }

  fn plain(text: &str) -> ViewUpdate {
    ViewUpdate { rows: vec![Row { start: 0, text: text.to_string() }], ..ViewUpdate::default() }
  }

  #[test]
  fn one_changed_character_costs_one_row_run_not_a_screen() {
    let before = compose(&plain("hello"), 40, 4);
    let after = compose(&plain("hellp"), 40, 4);
    assert_eq!(
      diff(&before, &after),
      concat!("\x1b[1;5H", "p", "\x1b[?25h", "\x1b[5 q", "\x1b[1;1H"),
      "the run starts at the first differing column and stops at the last; no column after it is re-sent"
    );
    assert!(
      diff(&before, &after).len() * 4 < full(&after).len(),
      "a keystroke must not cost a screen: {} bytes diffed, {} repainted",
      diff(&before, &after).len(),
      full(&after).len()
    );
  }

  #[test]
  fn a_row_that_got_shorter_is_erased_from_its_new_end() {
    let before = compose(&plain("hello"), 40, 4);
    let after = compose(&plain("hel"), 40, 4);
    assert_eq!(
      diff(&before, &after),
      concat!("\x1b[1;4H", "\x1b[K", "\x1b[?25h", "\x1b[5 q", "\x1b[1;1H"),
      "one EL from the new end, not a run of blanks followed by an EL"
    );
  }

  #[test]
  fn the_erase_is_never_emitted_while_the_attribute_is_inverted() {
    // Deleting inside a selection leaves the terminal inverted at the cut, and EL then paints the tail in reverse video.
    let mut selected = plain("ab");
    selected.cursor_quantum_start = 0;
    selected.cursor_quantum_end = 2;
    selected.span = 1;
    let after = compose(&selected, 40, 4);
    let before = compose(&plain("abcd"), 40, 4);
    assert_eq!(
      diff(&before, &after),
      concat!(
        "\x1b[1;1H", "\x1b[7m", "ab", "\x1b[0m", "\x1b[K",
        // The mode line is a real change too: the span went from 0 to 1.
        "\x1b[4;24H", "\x1b[7m", "1", "\x1b[0m",
        "\x1b[?25h", "\x1b[1 q", "\x1b[1;1H",
      )
    );
  }

  #[test]
  fn the_first_frame_of_a_session_repaints_everything() {
    let grid = compose(&plain("hello"), 40, 4);
    assert_eq!(frame(None, &grid, false), full(&grid));
  }

  #[test]
  fn a_full_view_repaints_everything_even_against_a_warm_cache() {
    let grid = compose(&plain("hello"), 40, 4);
    assert_eq!(frame(Some(&grid), &grid, true), full(&grid), "full: true is what discards cached rows");
  }

  #[test]
  fn a_geometry_change_repaints_everything_without_being_asked() {
    let before = compose(&plain("hello"), 40, 4);
    let after = compose(&plain("hello"), 42, 4);
    let painted = frame(Some(&before), &after, false);
    assert_eq!(painted, full(&after), "cached rows of the old width are not reusable");
    assert!(painted.contains("\x1b[4;1H"), "every row of the new grid is addressed");
  }

  #[test]
  fn an_ordinary_change_takes_the_diff_path() {
    let before = compose(&plain("hello"), 40, 4);
    let after = compose(&plain("hellp"), 40, 4);
    assert_eq!(frame(Some(&before), &after, false), diff(&before, &after));
  }

  #[test]
  fn a_cursor_that_only_moved_is_still_placed() {
    let mut before = compose(&plain("hello"), 40, 4);
    let mut after = before.clone();
    before.cursor = (0, 0);
    after.cursor = (0, 3);
    after.cursor_block = true;
    assert_eq!(
      diff(&before, &after),
      concat!("\x1b[?25h", "\x1b[1 q", "\x1b[1;4H"),
      "no cell changed, so the frame is the cursor alone - a diff that skipped it would leave the block on the old cell"
    );
  }

  /// A screen of text with a selection on it, like a real document: near-full rows, one inverted run.
  fn realistic(cols: usize, rows: usize, seed: char, typo: bool) -> Grid {
    let lines = (0..rows - 1)
      .map(|index| Row {
        start: (index * cols) as i64,
        text: {
          let mut text = std::iter::repeat_n(seed, cols).collect::<String>();
          if typo && index == rows / 2 {
            text.replace_range(12..13, "q");
          }
          text
        },
      })
      .collect::<Vec<_>>();
    let view = ViewUpdate {
      rows: lines,
      span: 1,
      cursor_quantum_start: 100,
      cursor_quantum_end: 140,
      cursor_row: 2,
      cursor_col: 7,
      editor_mode: "navigate".to_string(),
      editor_submode: "navigate".to_string(),
      cursor_quantum: "character".to_string(),
      ..ViewUpdate::default()
    };
    compose(&view, cols, rows)
  }

  fn median(samples: &mut Vec<std::time::Duration>) -> std::time::Duration {
    samples.sort();
    samples[samples.len() / 2]
  }

  fn time(mut body: impl FnMut() -> String) -> std::time::Duration {
    let mut samples = (0..200)
      .map(|_| {
        let started = std::time::Instant::now();
        let painted = body();
        std::hint::black_box(painted.len());
        started.elapsed()
      })
      .collect();
    median(&mut samples)
  }

  /// Ticket 06's open question: does diffing pay at 30-60 rows, or only in principle?
  /// `cargo test --release --bin visur-term measure_both -- --ignored --nocapture`
  #[test]
  #[ignore = "measurement, not a check"]
  fn measure_both_paint_paths() {
    println!("\n  viewport   path            bytes      median");
    for (cols, rows) in [(100, 30), (250, 70)] {
      let before = realistic(cols, rows, 'x', false);
      let after = realistic(cols, rows, 'x', true); // one character typed
      let scrolled = realistic(cols, rows, 'y', false); // every row changed, as scrolling does

      let full_time = time(|| full(&before));
      let one_time = time(|| diff(&before, &after));
      let scroll_time = time(|| diff(&before, &scrolled));
      println!(
        "  {cols}x{rows:<8} full repaint    {:>6}   {:>9.1} us",
        full(&before).len(),
        full_time.as_secs_f64() * 1e6
      );
      println!(
        "             diff, 1 char    {:>6}   {:>9.1} us",
        diff(&before, &after).len(),
        one_time.as_secs_f64() * 1e6
      );
      println!(
        "             diff, all rows  {:>6}   {:>9.1} us",
        diff(&before, &scrolled).len(),
        scroll_time.as_secs_f64() * 1e6
      );
    }
  }

  #[test]
  fn a_row_shorter_than_the_grid_is_erased_rather_than_padded_with_blanks() {
    let view = ViewUpdate {
      rows: vec![Row { start: 0, text: "ab".to_string() }],
      ..ViewUpdate::default()
    };
    assert!(
      full(&compose(&view, 40, 2)).contains("\x1b[1;1Hab\x1b[K"),
      "the tail of the row is one EL, not 38 spaces"
    );
  }
}
