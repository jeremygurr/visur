package com.ple.visur;

/**
 * What is on the other end of a socket, as the client itself says.
 *
 * <p>It exists for one question: where does an unsaved nameless buffer go? A terminal was launched inside a directory,
 * and a browser tab is not inside anything. Nothing else about editing differs between them, which is why this is not
 * a mode, a capability list, or a subtype.
 *
 * <p>Absence is the third answer and it is the common one in tests: a client that has not introduced itself has no
 * directory, so it writes no nameless swap.
 */
public enum ClientKind {
  TERMINAL,
  BROWSER;

  /**
   * The word the client sends for itself, or null when it said nothing useful.
   *
   * <p>A wrong word is not worth an error message: the only thing this decides is a directory, and a client that
   * cannot say what it is gets no nameless swap, which is what it would have got for no declaration at all.
   */
  public static ClientKind parse(String word) {
    if ("terminal".equals(word)) {
      return TERMINAL;
    }
    if ("browser".equals(word)) {
      return BROWSER;
    }
    return null;
  }
}
