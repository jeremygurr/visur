package com.ple.visur;

/**
 * The count typed <em>before</em> a key — {@code 12 s} selects twelve quanta — which is why it is a prefix and not a
 * suffix: a suffix would be one digit, and every key that takes a count shares this one.
 *
 * <p>It lives in the model rather than in a Java field because it is typed in one keypress and used in the next, and
 * the renderers have to be able to show it while it is pending.
 */
public class CountPrefix {

  /**
   * Folds {@code key} into the pending count. Only a lone digit in navigate mode counts; a digit in a submode, in
   * insert mode, or on the {@code ;} line belongs to whatever is being typed there.
   *
   * @return whether the keypress was consumed as part of the count, so nothing else should run for it
   */
  static boolean record(EditorModelCoupler emc, String key) {
    if (!isDigit(key) || emc.getIsInCommandState() || emc.getEditorSubmode() != EditorSubmode.navigate) {
      return false;
    }
    int digit = key.charAt(0) - '0';
    Integer pending = emc.session().getPendingCount();
    emc.session().putPendingCount(pending == null ? digit : pending * 10 + digit);
    return true;
  }

  /**
   * Retires the pending count once the keypress after it has run. The key that the count belonged to still needs to
   * read it, which is why this happens after execution rather than when the key is looked up — and why every key
   * clears it, not just Escape.
   *
   * <p>A count belongs to a key <em>sequence</em>, and a sequence is still in flight while a submode is open: the
   * count in {@code 3 ] w} is typed three keypresses before the jump, so {@code ]} must not consume it.
   */
  static void retireUnlessDigit(EditorModelCoupler emc, String key) {
    boolean sequenceStillInFlight = emc.getEditorSubmode() != EditorSubmode.navigate;
    if (!isDigit(key) && !sequenceStillInFlight) {
      emc.session().clearPendingCount();
    }
  }

  /** What a key sees when no count was typed: one. Zero is a count, not the absence of one. */
  static int countOrOne(EditorModelCoupler emc) {
    Integer pending = emc.session().getPendingCount();
    return pending == null ? 1 : pending;
  }

  private static boolean isDigit(String key) {
    return key.length() == 1 && Character.isDigit(key.charAt(0));
  }

}
