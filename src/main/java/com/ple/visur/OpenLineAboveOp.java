package com.ple.visur;

import java.util.ArrayList;

/**
 * {@code g}: one newline at the start of the enclosing line, which is an empty line above the caret.
 *
 * <p>The bounds come from the line indices and the cursor's row, not from the cursor quantum, {@code SPAN} or
 * {@code SCOPE}: at the word quantum the enclosing word's start would split the current line, and a key called "open a
 * line" that opens a line only sometimes is worse than one that ignores the quantum. The same is true of the separator
 * toggle — the line's own newline is found by index here, so there is no separator to include or exclude.
 */
public class OpenLineAboveOp implements Operator {
  @Override
  public void execute(Object ignored) {
    EditorModelCoupler emc = ServiceHolder.editorModelCoupler;
    String content = emc.getEditorContent();
    ArrayList<Integer> nl = emc.getNextLineIndices();
    int cy = emc.getCY();
    int lineStart = cy > 0 ? nl.get(cy - 1) : 0;
    emc.putEditorContent(content.substring(0, lineStart) + "\n" + content.substring(lineStart));
    // The index the newline went in at is the start of the line that just appeared, because it is that newline.
    emc.putCA(lineStart);
  }
}
