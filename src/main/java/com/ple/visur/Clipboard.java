package com.ple.visur;

import java.nio.charset.StandardCharsets;

/**
 * What visur last put on the user's clipboard, and how much a clipboard can be asked to hold.
 *
 * <p>The server never touches an OS clipboard: it asks a client to write one, and the client is the half on the user's
 * machine (over SSH that is the difference between the user's clipboard and some server's). So this class holds no
 * clipboard — it holds the <em>memory</em> of one. That memory is what makes a paste need no round trip: everything
 * copied inside visur was written by visur, so pasting it back is a lookup rather than a question.
 *
 * <p>Global, not per document or per window: there is one clipboard on the machine, and this server has one terminal
 * emulator hanging off it. It is static and reset with the model for the same reason {@link Connections} is — the state
 * outlives no client, and a test JVM that booted two servers must not inherit one's memory into the other's paste.
 */
public final class Clipboard {

  /**
   * The most text a client will be asked to write, counted the way the transport counts it: UTF-8 bytes.
   *
   * <p>OSC 52 is the limit, not the browser: mainstream emulators drop a paste request whose payload passes ~74999
   * characters, and base64 takes four characters per three bytes, so 56 000 bytes is the largest thing that arrives
   * whole everywhere. Truncating instead would put a <em>quietly shorter</em> text on the clipboard than the one the
   * user highlighted, which is data loss with extra steps; the yank stands and the clipboard is refused.
   */
  public static final int LIMIT_IN_UTF8_BYTES = 56_000;

  private static String cached;

  private Clipboard() {
  }

  /**
   * Text this window's command just copied, held until the command ends.
   *
   * <p>One offer per command, whichever way the command was written: {@code 3 p} displaces text three times and the
   * user's clipboard should be beeped at once, and a command that yanks and then deletes should leave the clipboard
   * holding what the command settled on rather than both. The flush lives with the command boundary, in
   * {@link KeyWasPressedVerticle}.
   */
  static void offer(Session window, String text) {
    int bytes = text.getBytes(StandardCharsets.UTF_8).length;
    if (bytes > LIMIT_IN_UTF8_BYTES) {
      // The yank has already happened, so the words are not lost — only the copy out is, and the message is where the
      // user learns to take a smaller one.
      window.reportError("clipboard: " + bytes + " bytes is more than a clipboard takes (" +
        LIMIT_IN_UTF8_BYTES + ")");
      return;
    }
    window.offerClipboardWrite(text);
  }

  /** What the last write put here; null when nothing has been written since the server started. */
  static String cached() {
    return cached;
  }

  /**
   * Remember this as what the clipboard holds. Called when a write is dispatched, and when a client answers a read —
   * the two ways the server can honestly say it knows what is out there.
   */
  static void cache(String text) {
    cached = text;
  }

  /** A new model remembers no previous model's clipboard. */
  static void reset() {
    cached = null;
  }
}
