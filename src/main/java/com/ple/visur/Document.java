package com.ple.visur;

import CursorPositionDC.CursorPositionDCHolder;
import DataClass.PrimitiveDataClassBrick;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;

/**
 * The text, and what it costs to know it: where it came from, what it would take to get back, and the undo ring that
 * walks it.
 *
 * <p>Everything here survives the window closing, which is the whole distinction this class exists for. Two windows
 * can be looking at one document, and then a keystroke in one is a change to the text both are showing and an edit
 * either of them can undo — while the cursor, the selection and the scroll position belong to one pair of hands and
 * die with the socket. See {@link Session} for the other half.
 *
 * <p>One document per process for now. The screens map turns that into a collection, and the only thing that makes
 * then easy is that there is already a thing to put in the collection.
 */
public class Document {

  /** Writing a few kilobytes takes microseconds, so half a second is only there to stop a held key from hammering the disk. */
  private static final long SWAP_DEBOUNCE_MS = 500;

  private String content = "";

  /**
   * The text as it last matched the file: loaded from it, or written to it. Dirty is {@code content} differing from
   * this, never a flag set on edit — an undo that puts the saved text back makes the document clean again, and that
   * sequence is the common case rather than a corner case.
   *
   * <p>A reference rather than a copy, because content is immutable and replaced wholesale.
   */
  private String savedContent = "";

  private String sourceFilePath;

  /**
   * The pool slot holding this document's text while it has no name, claimed by the first swap write and dropped when
   * the document gets a name. A document keeps the slot it got: the nameless buffer worked on for an hour is still
   * there tomorrow, in the same file.
   */
  private Path swapSlot;
  private final UndoHistory undoHistory = new UndoHistory();

  /**
   * Where every line in the text starts: one mutable cell, because it is a fact about the text and every window's
   * cursor is wired to it. Editing the text moves everybody's cursor the same way, and the data classes underneath do
   * that moving by invalidating whatever derives from a cell when the cell is written.
   */
  private final BrickVisurVar lineOffsets = BrickVisurVar.make(
    new CursorPositionDCHolder().wholeNumberListDC.makeBrick("nl", new ArrayList<>(), true));

  private long lastSwapWriteTime;

  public String getContent() {
    return content;
  }

  /**
   * New text, with the line offsets recomputed from it.
   *
   * <p>The one place the user's own text lands, which is why the swap hangs off it: text that has not reached a file
   * is the only kind worth mirroring, so this is the entry point that mirrors and {@link #putContentAsSaved} is not.
   */
  public void putContent(String newContent) {
    content = newContent;
    updateNextLineIndices();
    writeSwapFileDebounced(newContent);
  }

  /**
   * Text that came off the file, as opposed to text the user made: the buffer and the disk then hold the same thing,
   * so nothing is unsaved. {@code LoadFileOp} and the startup load are the two callers; swap recovery is deliberately
   * not one, since a swap is what survives a crash and its text has provably never reached the file.
   */
  public void putContentAsSaved(String newContent) {
    // Not `putContent`, and the swap is why: text that came off the file has nothing unsaved to mirror, and mirroring
    // it would overwrite the swap that says the buffer once held something else. That swap is evidence, and booting
    // onto a file is not a licence to destroy it.
    content = newContent;
    updateNextLineIndices();
    savedContent = content;
  }

  /**
   * The write succeeded, so whatever is in memory is on disk now — including after {@code ;"other.txt" saveFile},
   * where the baseline moves to a file that did not exist a moment ago.
   *
   * <p>The name says what the caller has achieved, not what this does: it moves the baseline, it does not set a flag.
   */
  public void markSaved() {
    savedContent = content;
  }

  /**
   * Whether this text has work in it that no file has. Compared rather than remembered, so that {@code d} then
   * {@code u} reads as clean: see {@link #savedContent}.
   */
  public boolean isDirty() {
    return !content.equals(savedContent);
  }

  /** The single character a one-character search would match from here, forwards or back. */
  public String getStrToMatch(boolean searchForwards, int startingPoint) {
    if (searchForwards && startingPoint < content.length()) {
      return content.substring(startingPoint, startingPoint + 1);
    }
    if (startingPoint > 0) {
      return content.substring(startingPoint - 1, startingPoint);
    }
    return "";
  }

  public ArrayList<Integer> getNextLineIndices() {
    return (ArrayList<Integer>) lineOffsets.getVal();
  }

  public void updateNextLineIndices() {
    ArrayList<Integer> indices = new ArrayList<>();
    for (int i = 0; i <= content.length(); i++) {
      if (i < content.length()) {
        if (content.charAt(i) == '\n') {
          indices.add(i + 1);
        }
      } else {
        indices.add(i);
      }
    }
    lineOffsets.putVal(indices);
  }

  /** The cell every window's cursor derives its row and column from. */
  public PrimitiveDataClassBrick lineOffsetsCell() {
    return lineOffsets.brick;
  }

  public UndoHistory getUndoHistory() {
    return undoHistory;
  }

  /** Null means this text has no name yet, which is a document that has never been saved rather than a missing value. */
  /**
   * Whether this window was told it is only looking, which is the answer someone gives when they are not sure. It
   * refuses a plain {@code :w} and nothing else: a path typed out, or a new file opened, are both louder than a flag.
   */
  private boolean readOnly;

  public boolean isReadOnly() {
    return readOnly;
  }

  public void putReadOnly(boolean readOnly) {
    this.readOnly = readOnly;
  }

  public String getSourceFilePath() {
    return sourceFilePath;
  }

  /**
   * Which file this text came from and goes back to.
   *
   * <p>Changing it abandons the old swap: a `.oldname.swp` left behind would offer its work for a file that is no
   * longer the one on screen, and the offer would be answered by whoever opened that old name next.
   */
  public void setSourceFilePath(String path) {
    String next = path == null ? "" : path;
    String current = sourceFilePath == null ? "" : sourceFilePath;
    if (next.equals(current)) {
      // Still the same file, still the same swap. `:open` of the file that is already open gets here, and treating
      // that as a rename would delete the swap of a document that has not changed hands.
      return;
    }
    deleteSwap();
    sourceFilePath = next;
  }

  /**
   * Mirror the text to disk, at most twice a second.
   *
   * <p>The half-second hole is real and accepted: a swap per keystroke is a disk write per keystroke, so the last
   * half-second of typing is what a crash costs. The write is also the only thing that claims a pool slot, so a
   * document that was opened and never typed into leaves no trace.
   */
  private void writeSwapFileDebounced(String text) {
    long now = System.currentTimeMillis();
    if (now - lastSwapWriteTime < SWAP_DEBOUNCE_MS) {
      return;
    }
    // Nothing to recover, nothing to leave a file for: a nameless empty buffer opening at boot must not claim a pool
    // slot before the user has typed anything.
    if (text.isEmpty()) {
      return;
    }
    lastSwapWriteTime = now;
    try {
      Path swap = swapToWrite();
      if (swap == null) {
        return;
      }
      Files.createDirectories(swap.getParent());
      Files.write(swap, text.getBytes(StandardCharsets.UTF_8),
        StandardOpenOption.WRITE, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    } catch (IOException failure) {
      // Non-fatal: if the swap write fails the document is still correct, and there is nothing on screen to put
      // the failure in until the next keypress anyway.
      System.out.println("Swap write failed: " + failure.getMessage());
    }
  }

  /**
   * Where the text is mirrored. A document with a name mirrors beside it; a nameless one takes a slot from the pool of
   * whichever window is typing, and holds that slot for as long as it is nameless.
   *
   * <p>Null is an answer and a safety: it means nowhere is known, which is what a window that has never introduced
   * itself gets. Without it every test that edits a document without a socket would be writing into the tree.
   */
  private Path swapToWrite() throws IOException {
    Path swap = swapPath();
    if (swap != null) {
      return swap;
    }
    Session window = ServiceHolder.editorModelCoupler == null ? null : ServiceHolder.editorModelCoupler.session();

    Path pool = SwapFiles.poolDir(window);
    if (pool == null) {
      return null;
    }
    swapSlot = SwapFiles.claimSlot(pool);
    return swapSlot;
  }

  /**
   * This document's swap, whether it is a slot claimed from the pool or a file beside the one it came from. Null for a
   * nameless document that has never written anything, which is the only kind that has no swap.
   */
  public Path swapPath() {
    if (sourceFilePath != null && !sourceFilePath.isEmpty()) {
      return SwapFiles.beside(new File(sourceFilePath));
    }
    return swapSlot;
  }

  /**
   * Delete the swap, because it has stopped being true: the text reached a file, or the user said to throw it away.
   *
   * <p>A swap that outlives its text is worse than no swap, since it is offered as work someone has already either
   * saved or rejected. The failure is printed and not fatal — the document is correct either way, and this runs at
   * moments (a save, a quit) where refusing the user's actual request would be the worse outcome.
   */
  public void deleteSwap() {
    Path swap = swapPath();
    swapSlot = null;
    if (swap == null) {
      return;
    }
    try {
      Files.deleteIfExists(swap);
    } catch (IOException failure) {
      System.out.println("Swap not deleted: " + failure.getMessage());
    }
  }
}
