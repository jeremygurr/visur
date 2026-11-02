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

fn row(grid: &Grid, index: usize) -> String {
  let width = painted_width(grid, index);
  let mut out = format!("\x1b[{};1H", index + 1);
  let mut inverse = false;
  for col in 0..width {
    let Cell { ch, inverse: wanted } = grid.cell(index, col);
    if wanted != inverse {
      out.push_str(if wanted { "\x1b[7m" } else { "\x1b[0m" });
      inverse = wanted;
    }
    out.push(ch);
  }
  if inverse {
    out.push_str("\x1b[0m"); // reset before erasing, or the tail is erased in reverse video
  }
  if width < grid.cols {
    out.push_str("\x1b[K"); // one erase instead of a row of blanks
  }
  out
}

/// A complete repaint of every row, then the cursor.
pub fn full(grid: &Grid) -> String {
  let mut out = String::with_capacity(grid.cols * grid.rows + 256);
  for index in 0..grid.rows {
    out.push_str(&row(grid, index));
  }
  let (row, col) = grid.cursor;
  out.push_str("\x1b[?25h");
  out.push_str(cursor_shape(grid.cursor_block));
  out.push_str(&format!("\x1b[{};{}H", row + 1, col + 1));
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
