package com.ple.visur;

/**
 * The text an insert session typed, kept for the session that is going to repeat it.
 *
 * <p>Three global variables, all owned by the keypress that entered insert mode with a count above 1: the count, what
 * goes between the copies it repeats, and the text so far. A session opened without a count has none of them, and
 * {@link #record} is a lookup that finds nothing — which is all this feature costs on the keys typed the other 99% of
 * the time.
 *
 * <p>The text is tracked by appending on insert and popping on a backspace, not read back out of the document as "the
 * text between where the session started and the caret". That reading cannot tell a character typed after arrowing
 * left from a character the session arrowed over, and once the caret walks back into text that predates the session a
 * backspace would subtract a character that was never added.
 */
public class SessionText {
  private static final String COUNT = "tempCount";
  private static final String SEPARATOR = "tempInsertSeparator";
  private static final String TYPED = "tempTypedText";

  private SessionText() {}

  /** Called on the way into insert mode, by {@link StashCountOp}, with the count already known to be worth repeating. */
  public static void begin(EditorModelCoupler emc, String separator) {
    emc.session().putGlobalVar(SEPARATOR, ObjectVisurVar.make(separator));
    emc.session().putGlobalVar(TYPED, ObjectVisurVar.make(""));
  }

  /** A session that will not repeat anything leaves nothing behind, so the next one cannot inherit it. */
  public static void discard(EditorModelCoupler emc) {
    emc.session().getGlobalVarMap().remove(COUNT);
    emc.session().getGlobalVarMap().remove(SEPARATOR);
    emc.session().getGlobalVarMap().remove(TYPED);
  }

  public static void record(EditorModelCoupler emc, String inserted) {
    String typed = typedSoFar(emc);
    if (typed == null) return;
    emc.session().putGlobalVar(TYPED, ObjectVisurVar.make(typed + inserted));
  }

  /** One backspace. Clamped at empty: a session that arrowed into old text can delete more than it typed. */
  public static void unrecord(EditorModelCoupler emc) {
    String typed = typedSoFar(emc);
    if (typed == null) return;
    emc.session().putGlobalVar(TYPED, ObjectVisurVar.make(typed.isEmpty() ? "" : typed.substring(0, typed.length() - 1)));
  }

  /**
   * Escape's work: the session's unit — what separates the copies, then what was typed — inserted {@code count - 1}
   * more times at the caret, so the document ends the way it would have if the typing had simply been done
   * {@code count} times. An empty session repeats the separator alone, which is what makes {@code 3 g} with nothing
   * typed three blank lines rather than one.
   *
   * <p>The range is re-derived afterwards at the span Escape restored: every copy went in under it, so bounds computed
   * against the text before the repeat point at characters that have moved.
   */
  public static void repeatAtCaret(EditorModelCoupler emc) {
    Integer count = stashedCount(emc);
    if (count == null || count <= 1) {
      discard(emc);
      return;
    }
    String unit = separator(emc) + typedSoFar(emc);
    StringBuilder repeated = new StringBuilder();
    for (int copy = 1; copy < count; copy++) {
      repeated.append(unit);
    }
    discard(emc);
    if (repeated.length() == 0) return;

    String content = emc.getEditorContent();
    int caret = emc.getCA();
    emc.putEditorContent(content.substring(0, caret) + repeated + content.substring(caret));
    int endOfLastCopy = caret + repeated.length();
    emc.putCA(endOfLastCopy);
    int[] bounds = emc.getCursorQuantum()
      .getBoundaries(endOfLastCopy, emc.getNextLineIndices(), emc.getSpan(), false);
    emc.putCursorQuantumStartAndScroll(bounds[0]);
    emc.putCursorQuantumEndAndScroll(bounds[1]);
  }

  private static Integer stashedCount(EditorModelCoupler emc) {
    VisurVar stashed = emc.session().getGlobalVar(COUNT);
    return stashed == null ? null : (Integer) stashed.getVal();
  }

  private static String separator(EditorModelCoupler emc) {
    VisurVar stashed = emc.session().getGlobalVar(SEPARATOR);
    return stashed == null ? "" : (String) stashed.getVal();
  }

  /** Null when this session is not recording, which is the common case and why this is not an empty string. */
  private static String typedSoFar(EditorModelCoupler emc) {
    VisurVar stashed = emc.session().getGlobalVar(TYPED);
    return stashed == null ? null : (String) stashed.getVal();
  }
}
