package com.ple.visur;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Whether the text in memory is the text on disk.
 *
 * <p>A comparison, not a flag, and {@link #undoingBackToTheSavedTextIsNotDirty} is the test that keeps it that way.
 * A flag set on edit and cleared on save passes every other test in this file and lies on the sequence users perform
 * most: delete something, undo it, and the document is what the file is again. The marker on that document would
 * announce work that does not exist.
 *
 * <p>No verticles and no sockets: the baseline is a property of the text, so the document answers it alone.
 */
class DocumentTest {

  @Test
  void anEmptyDocumentWithNoNameIsNotDirty() {
    assertFalse(new Document().isDirty(), "nothing has been typed and there is nothing to save");
  }

  @Test
  void textTypedIntoANamelessDocumentIsUnsavedWork() {
    Document document = new Document();
    document.putContent("x");
    assertTrue(document.isDirty(), "it has never reached a file, which is exactly what the marker means");
  }

  @Test
  void textThatArrivedFromTheFileIsAlreadySaved() {
    Document document = new Document();
    document.putContentAsSaved("from disk");
    assertFalse(document.isDirty(), "the buffer and the file hold the same text, which is the whole claim");
  }

  @Test
  void savingMovesTheBaselineToWhateverIsInMemory() {
    Document document = new Document();
    document.putContent("edited");
    document.markSaved();
    assertFalse(document.isDirty(), "the write succeeded, so whatever is here is now on disk");
  }

  /**
   * The one a flag cannot do: {@code d} then {@code u} leaves text identical to the file, so the document is not
   * dirty even though it was edited twice since the save.
   *
   * <p>{@code StringBuilder} is load-bearing. The last line has to hand back text that <em>equals</em> the saved text
   * without <em>being</em> it, because that is what retyping what you deleted produces — every edit in this editor
   * builds a fresh {@code String} by concatenation. Compare by reference and this test is the difference between a
   * marker that clears when the file is up to date and one that never does again.
   */
  @Test
  void undoingBackToTheSavedTextIsNotDirty() {
    Document document = new Document();
    document.putContentAsSaved("one\ntwo\n");
    document.putContent("one\n");
    assertTrue(document.isDirty(), "a line has gone and the file still has it");
    document.putContent(new StringBuilder("one\ntwo\n").toString());
    assertFalse(document.isDirty(), "the text is back byte for byte, in a different String; there is nothing left to save");
  }
}
