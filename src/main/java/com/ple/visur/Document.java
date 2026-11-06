package com.ple.visur;

import CursorPositionDC.CursorPositionDCHolder;
import DataClass.PrimitiveDataClassBrick;

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
  private String sourceFilePath;
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
   * <p>The one place content changes, which is why the swap file hangs off it: every path that can lose the user's
   * work — startup load, save, load, edit — goes through here or it does not change the document.
   */
  public void putContent(String newContent) {
    content = newContent;
    updateNextLineIndices();
    writeSwapFileDebounced(newContent);
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
  public String getSourceFilePath() {
    return sourceFilePath;
  }

  public void setSourceFilePath(String path) {
    sourceFilePath = path;
  }

  private void writeSwapFileDebounced(String text) {
    if (sourceFilePath == null || sourceFilePath.isEmpty()) {
      return;
    }
    long now = System.currentTimeMillis();
    if (now - lastSwapWriteTime < SWAP_DEBOUNCE_MS) {
      return;
    }
    lastSwapWriteTime = now;
    try {
      Path swapPath = Paths.get(sourceFilePath + ".visur-swap");
      Files.write(swapPath, text.getBytes(StandardCharsets.UTF_8),
        StandardOpenOption.WRITE, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    } catch (IOException failure) {
      // Non-fatal: if the swap write fails the document is still correct, and there is nothing on screen to put
      // the failure in until the next keypress anyway.
      System.out.println("Swap write failed: " + failure.getMessage());
    }
  }
}
