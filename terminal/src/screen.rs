// Component 4 of the design: the shadow becomes a grid of cells. The grid is the whole vocabulary the emitter
// below it knows, and it is the previous frame the compositor will diff against in ticket 06.
//
// There is no wrapping arithmetic here, deliberately: rows arrive wrapped, so a row *is* a screen row. The only
// arithmetic is clipping a row that is wider than the terminal, which the server should never send.

use crate::protocol::ViewUpdate;

#[derive(Debug, Clone, Copy, PartialEq, Eq, Default)]
pub struct Cell {
  pub ch: char,
  pub inverse: bool,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct Grid {
  pub cols: usize,
  pub rows: usize,
  cells: Vec<Cell>,
  /// Where to put the terminal's cursor, as a zero-based (row, column) inside the grid.
  pub cursor: (usize, usize),
  /// A span covering anything is drawn as a block, a zero-cell span as a bar (spec §5.5).
  pub cursor_block: bool,
}

impl Grid {
  pub fn cell(&self, row: usize, col: usize) -> Cell {
    self.cells[row * self.cols + col]
  }

  pub fn blank(cols: usize, rows: usize) -> Self {
    Grid {
      cols,
      rows,
      cells: vec![Cell { ch: ' ', inverse: false }; cols * rows],
      cursor: (0, 0),
      cursor_block: false,
    }
  }

  fn set(&mut self, row: usize, col: usize, cell: Cell) {
    self.cells[row * self.cols + col] = cell;
  }
}

/// The line drawn on the last row: mode, submode, quantum, span, search target, or the command line.
pub fn mode_line(view: &ViewUpdate, cols: usize) -> String {
  if view.is_in_command_state {
    return clip(&command_line(view), cols);
  }
  let label = if view.editor_mode == view.editor_submode {
    format!(" {} MODE ", view.editor_mode.to_uppercase())
  } else {
    format!(" {} SUBMODE ", view.editor_submode.to_uppercase())
  };
  let searching = view.editor_submode == "searchForward" || view.editor_submode == "searchBackward";
  let tail = if searching {
    let direction = if view.editor_submode == "searchForward" { 'f' } else { 'F' };
    format!(" {}({}) {} ", view.scope_quantum.to_uppercase(), direction, view.search_target)
  } else {
    format!(" {} QUANTUM  SPAN={} ", view.cursor_quantum.to_uppercase(), view.span)
  };
  pad(&format!("{label}{tail}"), cols)
}

fn command_line(view: &ViewUpdate) -> String {
  let mut line = String::from(";");
  for (index, ch) in view.command_state_content.chars().enumerate() {
    if index as i64 == view.command_cursor {
      line.push('|');
    }
    line.push(ch);
  }
  if view.command_cursor as usize >= view.command_state_content.chars().count() {
    line.push('|');
  }
  line
}

fn pad(text: &str, cols: usize) -> String {
  let mut line = clip(text, cols);
  line.extend(std::iter::repeat_n(' ', cols - line.chars().count()));
  line
}

fn clip(text: &str, cols: usize) -> String {
  text.chars().take(cols).collect()
}

fn is_selected(view: &ViewUpdate, absolute: i64) -> bool {
  view.span > 0 && absolute >= view.cursor_quantum_start && absolute < view.cursor_quantum_end
}

/// The grid the terminal paints. Content rows come first; the mode line takes the last row.
pub fn compose(view: &ViewUpdate, cols: usize, rows: usize) -> Grid {
  let mut grid = Grid::blank(cols, rows);
  let content_rows = rows.saturating_sub(1);

  for (index, row) in view.rows.iter().take(content_rows).enumerate() {
    for (offset, ch) in row.text.chars().take(cols).enumerate() {
      let absolute = row.start + offset as i64;
      grid.set(index, offset, Cell { ch, inverse: is_selected(view, absolute) });
    }
  }

  let line = mode_line(view, cols);
  for (col, ch) in line.chars().enumerate() {
    grid.set(rows - 1, col, Cell { ch, inverse: true });
  }

  let row = (view.cursor_row.max(0) as usize).min(content_rows.saturating_sub(1));
  let col = (view.cursor_col.max(0) as usize).min(cols.saturating_sub(1));
  grid.cursor = (row, col);
  grid.cursor_block = view.span > 0;
  grid
}

#[cfg(test)]
mod tests {
  use super::*;
  use crate::protocol::{Row, ViewUpdate};

  fn view(rows: &[(i64, &str)]) -> ViewUpdate {
    ViewUpdate {
      rows: rows.iter().map(|(start, text)| Row { start: *start, text: text.to_string() }).collect(),
      ..ViewUpdate::default()
    }
  }

  fn line(grid: &Grid, row: usize) -> String {
    (0..grid.cols).map(|col| grid.cell(row, col).ch).collect()
  }

  fn inverse(grid: &Grid, row: usize) -> String {
    (0..grid.cols)
      .filter(|col| grid.cell(row, *col).inverse)
      .map(|col| grid.cell(row, col).ch)
      .collect()
  }

  #[test]
  fn each_row_lands_on_its_own_screen_row() {
    let grid = compose(&view(&[(0, "abc"), (3, "de")]), 10, 4);
    assert_eq!(line(&grid, 0), "abc       ");
    assert_eq!(line(&grid, 1), "de        ");
    assert_eq!(line(&grid, 2), "          ", "a row the server did not send is blank");
  }

  #[test]
  fn the_selection_is_the_absolute_span_clipped_to_each_row() {
    let mut v = view(&[(100, "abcdef"), (106, "ghij")]);
    v.cursor_quantum_start = 103;
    v.cursor_quantum_end = 108;
    v.span = 1;
    let grid = compose(&v, 10, 4);
    assert_eq!(inverse(&grid, 0), "def", "row 100..106 holds only the span's first three characters");
    assert_eq!(inverse(&grid, 1), "gh", "the span ends at 108, so i and j are outside it");
  }

  #[test]
  fn nothing_is_selected_when_the_span_is_zero() {
    let mut v = view(&[(0, "abcdef")]);
    v.cursor_quantum_start = 2;
    v.cursor_quantum_end = 2;
    v.span = 0;
    assert_eq!(inverse(&compose(&v, 10, 4), 0), "");
  }

  #[test]
  fn the_cursor_is_a_block_whenever_anything_is_selected_and_a_bar_below_that() {
    let mut v = view(&[(0, "abcdef")]);
    v.span = 1;
    assert!(compose(&v, 10, 4).cursor_block);
    v.span = 3;
    assert!(compose(&v, 10, 4).cursor_block, "a wide selection is still one cursor");
    v.span = 0;
    assert!(!compose(&v, 10, 4).cursor_block);
  }

  #[test]
  fn the_cursor_row_and_column_are_clamped_into_the_grid() {
    let mut v = view(&[(0, "ab")]);
    v.cursor_row = 99;
    v.cursor_col = 99;
    let grid = compose(&v, 10, 4);
    assert_eq!(grid.cursor, (2, 9), "clamped to the last content row and last column");
  }

  #[test]
  fn a_row_wider_than_the_grid_is_clipped() {
    let grid = compose(&view(&[(0, "abcdefghijkl")]), 5, 3);
    assert_eq!(line(&grid, 0), "abcde");
  }

  #[test]
  fn the_mode_line_names_the_mode_and_the_quantum() {
    let mut v = view(&[]);
    v.editor_mode = "navigate".to_string();
    v.editor_submode = "navigate".to_string();
    v.cursor_quantum = "character".to_string();
    v.span = 1;
    assert_eq!(mode_line(&v, 60).trim_end(), " NAVIGATE MODE  CHARACTER QUANTUM  SPAN=1");
  }

  #[test]
  fn the_mode_line_names_the_submode_when_it_differs_and_shows_a_search_target() {
    let mut v = view(&[]);
    v.editor_mode = "navigate".to_string();
    v.editor_submode = "searchForward".to_string();
    v.scope_quantum = "line".to_string();
    v.search_target = "necess".to_string();
    assert_eq!(mode_line(&v, 60).trim_end(), " SEARCHFORWARD SUBMODE  LINE(f) necess");
  }

  #[test]
  fn the_mode_line_shows_the_command_state_with_its_cursor() {
    let mut v = view(&[]);
    v.is_in_command_state = true;
    v.command_state_content = "write".to_string();
    v.command_cursor = 2;
    // The cursor is a bar *before* character 2, not a character of its own.
    assert_eq!(mode_line(&v, 60), ";wr|ite");
  }

  #[test]
  fn the_mode_line_is_padded_and_clipped_to_the_grid() {
    let mut v = view(&[]);
    v.editor_mode = "navigate".to_string();
    v.editor_submode = "navigate".to_string();
    assert_eq!(mode_line(&v, 8).chars().count(), 8);
    assert_eq!(mode_line(&v, 40).chars().count(), 40, "padded to the grid width");
    assert_eq!(mode_line(&v, 3).chars().count(), 3, "clipped to the grid width");
  }

  #[test]
  fn the_mode_line_occupies_the_last_row_of_the_grid() {
    let mut v = view(&[(0, "abc"), (3, "def"), (6, "ghi")]);
    v.editor_mode = "navigate".to_string();
    v.editor_submode = "navigate".to_string();
    let grid = compose(&v, 12, 3);
    assert_eq!(grid.rows, 3);
    assert_eq!(line(&grid, 0), "abc         ");
    assert_eq!(line(&grid, 1), "def         ");
    assert_eq!(line(&grid, 2), " NAVIGATE MO", "the third row of the grid is the mode line");
    assert!(grid.cell(2, 0).inverse, "the mode line is inverse video");
  }
}
