package com.ple.visur;

import java.util.ArrayList;

/**
 * {@code G}: one newline just past the end of the enclosing line, which is an empty line below the caret.
 *
 * <p>Bounds come from the line indices and the cursor's row, not from the cursor quantum, {@code SPAN} or the separator
 * toggle — see {@link OpenLineAboveOp} for why a key that opens a line reads none of them.
 *
 * <p>Whether the line has a newline of its own decides where the caret goes, and it is the only thing that does. When
 * it does, the inserted newline lands after that one and terminates the new empty line itself, so the caret goes at the
 * insertion index. When it does not — the last line of a document with no trailing newline — the inserted newline
 * terminates the text line instead, and the caret goes past it, on the empty line the document now ends with.
 */
public class OpenLineBelowOp implements Operator {
  @Override
  public void execute(Object ignored) {
    EditorModelCoupler emc = ServiceHolder.editorModelCoupler;
    String content = emc.getEditorContent();
    ArrayList<Integer> nl = emc.getNextLineIndices();
    int nextLineStart = nl.get(emc.getCY()); // the index just past this line's newline, or the end of the content
    boolean lineHasItsOwnNewline = nextLineStart > 0 && content.charAt(nextLineStart - 1) == '\n';
    emc.putEditorContent(content.substring(0, nextLineStart) + "\n" + content.substring(nextLineStart));
    emc.putCA(lineHasItsOwnNewline ? nextLineStart : nextLineStart + 1);
  }
}
