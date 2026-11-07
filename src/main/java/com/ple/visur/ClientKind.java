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
  BROWSER
}
