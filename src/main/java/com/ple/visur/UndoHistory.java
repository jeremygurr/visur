package com.ple.visur;

import io.vertx.core.shareddata.Shareable;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * The snapshots {@code u} and {@code R} walk: whole commands, back and forward.
 *
 * <p>A snapshot is taken of the state before a command and pushed only if the command changed content, at the one place
 * a command runs ({@link KeyWasPressedVerticle#press}). An inverse operation per operator would be one chance per
 * operator to corrupt the document quietly, which is the exact failure undo exists to prevent; a snapshot cannot get a
 * boundary wrong.
 *
 * <p><b>The unit is a command and the boundary is entering navigate mode.</b> {@code beforeCommand} records the state
 * once and holds it while a mode other than navigate is open, so a whole insert session — {@code g}, the typing, the
 * repeat — is one unit, and the command that returns to navigate mode closes it. A command that changed nothing pushes
 * nothing, which is why {@code u} after {@code 5 l} does nothing rather than appearing broken.
 */
public class UndoHistory implements Shareable {
  /** A guess, and one constant: a 5 KB document per command, 200 commands deep. */
  private static final int RING_SIZE = 200;

  private final Deque<Snapshot> undoStack = new ArrayDeque<>();
  private final Deque<Snapshot> redoStack = new ArrayDeque<>();

  /**
   * The state before the command in flight, taken by its first keypress and held across an insert session.
   *
   * <p>{@code undo} and {@code redo} set it to null themselves: the state they arrive at is the state they leave, and
   * neither belongs on the undo stack.
   */
  private Snapshot pending;

  /**
   * Everything a command can put back. Not the yank register: a register restored from a snapshot can name text that is
   * no longer in the document, so undo leaves it holding whatever the displaced text was.
   */
  private record Snapshot(String content, int cursorQuantumStart, int cursorQuantumEnd, int span, int ca) {
    static Snapshot of(EditorModelCoupler emc) {
      return new Snapshot(emc.getEditorContent(), emc.getCursorQuantumStart(), emc.getCursorQuantumEnd(),
          emc.getSpan(), emc.getCA());
    }

    void restore(EditorModelCoupler emc) {
      emc.putEditorContent(content);
      emc.putSpan(span);
      emc.putCA(ca);
      emc.putCursorQuantumStartAndScroll(cursorQuantumStart);
      emc.putCursorQuantumEndAndScroll(cursorQuantumEnd);
    }
  }

  void beforeCommand(EditorModelCoupler emc) {
    if(pending == null) {
      pending = Snapshot.of(emc);
    }
  }

  void afterCommand(EditorModelCoupler emc) {
    if(pending == null || emc.getEditorSubmode() != EditorSubmode.navigate) {
      return; // mid-session: the unit is not over yet
    }
    if(!pending.content.equals(emc.getEditorContent())) {
      push(undoStack, pending);
      redoStack.clear(); // a new edit invalidates everything redo was offering
    }
    pending = null;
  }

  void undo(EditorModelCoupler emc) {
    pending = null;
    if(undoStack.isEmpty()) {
      return; // nothing to undo is a no-op, not an error
    }
    push(redoStack, Snapshot.of(emc));
    undoStack.removeFirst().restore(emc);
  }

  void redo(EditorModelCoupler emc) {
    pending = null;
    if(redoStack.isEmpty()) {
      return;
    }
    push(undoStack, Snapshot.of(emc));
    redoStack.removeFirst().restore(emc);
  }

  /** A different document is not this document's history: {@code u} must never paste one file into another. */
  public void clear() {
    undoStack.clear();
    redoStack.clear();
    pending = null;
  }

  private void push(Deque<Snapshot> stack, Snapshot snapshot) {
    stack.addFirst(snapshot);
    while(stack.size() > RING_SIZE) {
      stack.removeLast();
    }
  }
}
