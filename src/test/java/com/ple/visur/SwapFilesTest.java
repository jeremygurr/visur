package com.ple.visur;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Where a swap file goes, and which one it gets.
 *
 * <p>Two rules with real consequences behind them. A nameless buffer — the one kind that cannot be recovered by
 * {@code :open path}, because it has no path — has to land somewhere a person can find again, from a pool nobody
 * overwrites. And the directory is not arbitrary: the terminal was launched inside the work, a browser tab is nowhere.
 */
class SwapFilesTest {
  @TempDir
  Path dir;

  @Test
  void theFirstNamelessDocumentClaimsTheFirstSlotOfThePool() throws IOException {
    Path slot = SwapFiles.claimSlot(dir);

    assertEquals(".swa", slot.getFileName().toString(), "the pool is vim's letters, in order");
    assertTrue(Files.exists(slot), "claiming a slot means it exists, so two windows cannot both take it");
  }

  @Test
  void twoNamelessDocumentsClaimTwoDifferentSlots() throws IOException {
    Path first = SwapFiles.claimSlot(dir);
    Path second = SwapFiles.claimSlot(dir);

    assertNotEquals(first, second, "one slot holding two buffers is one buffer recovered and one lost");
    assertEquals(".swb", second.getFileName().toString());
  }

  /**
   * The rule that makes the pool safe without a lock: a slot is taken by existing, so a slot that already exists is
   * somebody else's work. Overwriting it is not a race to be won, it is a buffer deleted.
   */
  @Test
  void aSlotThatExistsIsNeverOverwritten() throws IOException {
    Path taken = dir.resolve(".swa");
    String somebodyElsesWork = "an hour of typing nobody has named yet\n";
    Files.write(taken, somebodyElsesWork.getBytes(StandardCharsets.UTF_8));

    Path claimed = SwapFiles.claimSlot(dir);

    assertNotEquals(taken, claimed);
    assertEquals(somebodyElsesWork, Files.readString(taken), "the pool holds work; a claim may not spend it");
  }

  @Test
  void theSecondRoundOfThePoolStartsAtSwaWithAOneOnTheEnd() throws IOException {
    for (char letter = 'a'; letter <= 'z'; letter++) {
      Files.createFile(dir.resolve(".sw" + letter));
    }

    Path claimed = SwapFiles.claimSlot(dir);

    assertEquals(".swa1", claimed.getFileName().toString(), "twenty-six nameless buffers is a generous Tuesday");
  }

  /**
   * A client that has never sent a {@code viewportWasChanged} has no directory, and that is the safe answer rather than
   * a gap: the server would otherwise be writing into a directory it knows nothing about. Every test that drives the
   * model without a socket relies on this, and so does the repository.
   */
  @Test
  void aWindowThatHasNotIntroducedItselfHasNoPoolToWriteInto() {
    Session unknown = new Session();

    assertNull(SwapFiles.poolDir(unknown));
  }

  @Test
  void aTerminalWindowPoolsWhereItWasLaunchedAndABrowserWindowInTheStateDir() {
    Session terminal = new Session();
    terminal.declareClientKind(ClientKind.TERMINAL);
    Session browser = new Session();
    browser.declareClientKind(ClientKind.BROWSER);

    assertEquals(Path.of(System.getProperty("user.dir")), SwapFiles.poolDir(terminal),
      "the nameless buffer belongs to the directory it was typed in, the way a swap belongs beside its file");
    assertEquals(Path.of(System.getProperty("user.home"), ".local/state/visur/browser"), SwapFiles.poolDir(browser),
      "a browser tab has no project, so its unsaved names belong to visur");
  }

  /** Every test that boots a server for real points this at a temp directory, so no suite ever writes into the repo. */
  @Test
  void thePoolDirectoryCanBeOverridden() throws IOException {
    String previous = System.getProperty("visur.swapDir");
    System.setProperty("visur.swapDir", dir.toString());
    try {
      Session browser = new Session();
      browser.declareClientKind(ClientKind.BROWSER);

      assertEquals(dir, SwapFiles.poolDir(browser), "a swap directory a test cannot point at is a swap directory that"
        + " gets written into the home directory of whoever ran the tests");
    } finally {
      if (previous == null) {
        System.clearProperty("visur.swapDir");
      } else {
        System.setProperty("visur.swapDir", previous);
      }
    }
  }
}
