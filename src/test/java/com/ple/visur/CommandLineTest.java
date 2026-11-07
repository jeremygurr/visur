package com.ple.visur;

import io.vertx.rxjava3.core.Vertx;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Stack;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The `:` commands, typed.
 *
 * <p>Every test here presses the keys and reads what the document and the screen ended up as, because the command line
 * is a surface a human types into and that is the seam it is used at. {@link FileIoTest} builds its operators by hand —
 * {@code new SaveFileOp().execute(null)} — which is precisely how {@code loadFile /tmp/x.txt} sat broken for as long as
 * it did: the operator was fine, the *sentence* never reached it, and nothing between the keyboard and the operator was
 * being tested.
 *
 * <p>What the command line says is one grammar and not the editor's sentence grammar: `:w`, `:open path`, `:q`, `:q!`.
 * The raw sentence door (`;"word" quantumStart`) is gone with the key that opened it, so a word that is not a command is
 * an error naming itself rather than a compile that stops early and does nothing.
 */
class CommandLineTest {
  private static final String CONTENT = "one two\n";

  private Vertx vertx;
  private EditorModelCoupler emc;

  @TempDir
  Path tempDir;

  @BeforeEach
  void setUp() {
    vertx = Vertx.vertx();
    ServiceHolder.init();
    ServiceHolder.initializerService.initializeEditorModel();
    emc = ServiceHolder.editorModelCoupler;
    emc.document().setSourceFilePath(""); // keep the swap-file hook away from the working tree
    emc.document().putContentAsSaved(CONTENT); // as if it had just been loaded: nothing unsaved yet
    emc.session().putCanvasWidth(80);
    emc.session().putCanvasHeight(24);
    emc.session().putCanvasStart(0);
    emc.session().putCA(0);
    Stack<EditorSubmode> submodes = new Stack<>();
    submodes.push(EditorSubmode.navigate);
    emc.session().putEditorSubmodeStack(submodes);
  }

  @AfterEach
  void tearDown() {
    vertx.close();
  }

  /** One command, typed: the door, the words, and the `Enter` that runs them. */
  private void typeCommand(String line) {
    press(":");
    for (String key : line.split("")) {
      press(key);
    }
    press("Enter");
  }

  private void press(String key) {
    KeyWasPressedVerticle.press(emc, KeyPressed.from(key), key);
  }

  private String message() {
    return View.of(emc).render().getString("message");
  }

  @Test
  void colonWSavesTheRememberedFileAndTakesTheUnsavedMarkerOff() throws IOException {
    Path file = tempDir.resolve("remembered.txt");
    emc.document().setSourceFilePath(file.toString());
    press("i");
    press("x");
    press("Escape");
    assertTrue(emc.document().isDirty(), "the typing is unsaved work");

    typeCommand("w");

    assertEquals("x" + CONTENT, Files.readString(file), "the remembered file got the text");
    assertFalse(emc.document().isDirty(), "and is now the text, so the marker is a lie if it is still up");
  }

  /**
   * The headline of the ticket, and the thing that had never worked: `loadFile /tmp/x.txt` compiled to
   * {@code LoadFileOp} <em>alone</em>, because a path only compiled when it began with a quote. So the operator ran with
   * no argument, reported that it wanted one, and every test that called the operator by hand stayed green.
   */
  @Test
  void aBarePathAfterColonWSavesThereAndBecomesTheRememberedFile(@TempDir Path elsewhere) throws IOException {
    emc.document().setSourceFilePath(tempDir.resolve("old.txt").toString());
    Path target = elsewhere.resolve("new.txt");
    press("i");
    press("x");
    press("Escape");

    typeCommand("w " + target);

    assertEquals("x" + CONTENT, Files.readString(target), "the path typed without quotes is the file written");
    assertEquals(target.toString(), emc.document().getSourceFilePath(), "and the document remembers it from now on");
    assertFalse(emc.document().isDirty());
  }

  /**
   * Quotes survive as the way to keep a space, and are otherwise noise. A trailing space typed after a path is a thing
   * users type, and a directory entry called {@code notes.txt } is a bug report with a filename; the trimming is
   * asserted here rather than discovered there. What it costs is a path whose <em>last</em> character is a space, which
   * no longer survives — said in {@code docs/reference/keys.md}, not hidden.
   */
  @Test
  void spacesAroundThePathAreNotPartOfIt() throws IOException {
    Path target = tempDir.resolve("notes.txt");

    typeCommand("w    " + target + "   ");

    assertTrue(Files.exists(target), "the path is the path, however many spaces were typed around it");
    assertEquals(0, Files.list(tempDir).filter(p -> p.getFileName().toString().startsWith("notes.txt ")).count(),
      "no file was written with a trailing space in its name");
  }

  /** Quotes are still honoured, which is also what lets a path contain a space at all. */
  @Test
  void aQuotedPathKeepsItsSpace() throws IOException {
    Path target = tempDir.resolve("My Docs.txt");

    typeCommand("w \"" + target + "\"");

    assertTrue(Files.exists(target), "quotes hold a path with a space in it together");
  }

  @Test
  void colonOpenLoadsTheFileNamedWithoutQuotes() throws IOException {
    Path other = tempDir.resolve("other.txt");
    Files.writeString(other, "loaded text\n");

    typeCommand("open " + other);

    assertEquals("loaded text\n", emc.document().getContent());
    assertEquals(other.toString(), emc.document().getSourceFilePath());
    assertFalse(emc.document().isDirty(), "text that came out of a file is not unsaved work");
  }

  /**
   * `:open` on a dirty document is the one command that would destroy work, and it is refused rather than confirmed:
   * visur has no question-and-answer surface yet, and a refusal that names the way round it needs none.
   */
  @Test
  void colonOpenRefusesWhileThereIsUnsavedWork() throws IOException {
    Path other = tempDir.resolve("other.txt");
    Files.writeString(other, "loaded text\n");
    press("i");
    press("x");
    press("Escape");

    typeCommand("open " + other);

    assertEquals("x" + CONTENT, emc.document().getContent(), "the work that was not saved is still there");
    assertTrue(message().contains("unsaved"), "and the screen says why in those words: " + message());
  }

  /** The exact words the map asked for, because a user searching for the error needs the string, not a paraphrase. */
  @Test
  void colonWWithNoFileToWriteToSaysThereIsNoFilename() {
    press("i");
    press("x");
    press("Escape");

    typeCommand("w");

    assertEquals("Error writing: No filename provided", message());
    assertTrue(emc.document().isDirty(), "nothing was written, so the marker stays up");
  }

  @Test
  void colonOpenWithNoPathAsksForOne() {
    typeCommand("open");

    assertTrue(message().contains(":open"), "the complaint names the command that needs an argument: " + message());
    assertEquals(CONTENT, emc.document().getContent());
  }

  /**
   * A typo used to be a variable recall: {@code frobnicate} compiled to zero ops and reported nothing, because the
   * matcher list ends in a catch-all and the compile loop stops at the first word nothing matched. One that says which
   * word it did not know is the difference between a typo and a silent failure.
   */
  @Test
  void anUnknownCommandNamesTheWordItDidNotKnow() {
    typeCommand("frobnicate");

    assertTrue(message().contains("frobnicate"), "the message quotes back what was typed: " + message());
    assertEquals(CONTENT, emc.document().getContent(), "and nothing else happened");
  }

  /**
   * The command line is a fresh utterance, so nothing an earlier command left on the scratch stack belongs to it.
   * {@code SaveFileOp} takes its path from that stack, which is how a leftover word became a filename once already (see
   * {@link ChangeCursorQuantumOpTest}) — the general fix being that every command-line command starts by clearing it.
   */
  @Test
  void aWordLeftBehindByAnEarlierCommandIsNotTheFileToWrite() throws IOException {
    Path target = tempDir.resolve("wanted.txt");
    emc.session().putOnExecutionDataStack("tempCursorQuantum");

    typeCommand("w " + target);

    assertTrue(Files.exists(target), "the save went to the path typed, not to the leftover");
    assertFalse(Files.exists(tempDir.resolve("tempCursorQuantum")), "and the leftover did not become a file either");
  }

  /**
   * `:q` over unsaved work is the same refusal as `:open`: visur has no surface for a question, so the command says no
   * and names the key that means "I know". Naming `:q!` in the message is what makes the refusal one keystroke from an
   * answer rather than a dead end the user has to look up.
   */
  @Test
  void colonQRefusesWhileThereIsUnsavedWork() {
    press("i");
    press("x");
    press("Escape");

    typeCommand("q");

    assertTrue(message().contains(":q!"), "the refusal names the way past it: " + message());
    assertFalse(emc.session().getQuitWasRequested(), "and nobody is on their way out");
    assertEquals("x" + CONTENT, emc.document().getContent(), "the work is still there to save");
  }

  /**
   * Leaving is a window leaving, not the server stopping: the flag is per session, and the socket that set it is the one
   * that closes. Who stops the server is {@code ZD} and the launcher, because a browser tab typing {@code :q!} must not
   * be able to end another window's session, and stopping a process nobody started is not the editor's job.
   */
  @Test
  void colonQOnASavedDocumentAsksToBeLetGo() {
    typeCommand("q");

    assertTrue(emc.session().getQuitWasRequested(), "the window asked, and the socket that asked is what goes away");
    assertEquals("", message(), "a command that worked has nothing to report");
  }

  /**
   * `:q!` is the discard, and the discard has to reach the disk: the swap file is how discarded text comes back at the
   * next boot, which is the opposite of what was just asked for. The file being edited is left alone — discarding the
   * buffer says nothing about what is on disk.
   */
  @Test
  void colonQBangDiscardsTheSwapFileAndLeaves() throws IOException {
    Path file = tempDir.resolve("discard.txt");
    Files.writeString(file, "what the file has\n");
    Path swap = Path.of(file + ".visur-swap");
    Files.writeString(swap, "what only the buffer has\n");
    emc.document().setSourceFilePath(file.toString());
    emc.document().putContentAsSaved("what the file has\n");
    press("i");
    press("x");
    press("Escape");

    typeCommand("q!");

    assertTrue(emc.session().getQuitWasRequested(), "the refusal that `:q` would have given is not given");
    assertFalse(Files.exists(swap), "a swap is how discarded work comes back, and this work was discarded on purpose");
    assertEquals("what the file has\n", Files.readString(file), "the file on disk was never part of the bargain");
  }

  /** A document that has never had a name has no swap to forget, and leaving is still leaving. */
  @Test
  void colonQBangOnAnUnnamedDocumentStillLeaves() {
    typeCommand("q!");

    assertTrue(emc.session().getQuitWasRequested());
    assertEquals("", message(), "having nothing to discard is not an error");
  }

  /**
   * Distinguishable in the compiled command, not only at execution: `:w` with a path carries the path, `:w` without one
   * carries nothing, so "no argument" and "empty argument" are not the same command and cannot silently become each
   * other. `clearEDS` is the head of both, which is what makes the test above pass.
   */
  @Test
  void aCommandWithAPathIsNotTheSameCommandAsOneWithout() {
    assertEquals(2, ServiceHolder.commandCompileService.compileCommandLine("w").ops.size(), "clearEDS + saveFile");
    assertEquals(3, ServiceHolder.commandCompileService.compileCommandLine("w /tmp/x.txt").ops.size(),
      "clearEDS + the path + saveFile");
    assertTrue(ServiceHolder.commandCompileService.compileCommandLine("open /tmp/x.txt").ops.stream()
        .anyMatch(op -> op instanceof BarePathOp),
      "the path is an op of its own, so the load has something to pop");
  }
}
