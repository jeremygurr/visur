package com.ple.visur;

/**
 * {@code p} replaces the selection with the register, then hands the register what the paste displaced.
 *
 * <p>The text it removes is exactly the selection on screen — never {@code operatorExtent()}'s count × span. A key
 * called "paste" deleting four words nobody highlighted is the failure ticket 12 refused for {@code d} with more
 * force; the count multiplies the copies inserted, not the text removed.
 *
 * <p>Over nothing — no selection, or an empty range such as a blank line at the word quantum — nothing is displaced
 * and the register keeps what it held, so {@code p p} pastes twice instead of pasting once and then nothing.
 */
public class PasteYankRegisterOp implements Operator {
  @Override
  public void execute(Object ignored) {
    EditorModelCoupler emc = ServiceHolder.editorModelCoupler;
    VisurVar register = emc.session().getGlobalVar("yankRegister");
    if (register == null) return;
    String value = ((String) register.getVal()).replace("\r\n", "\n");
    // A count of zero pastes nothing, and an operator that does nothing does not displace text either: the guard runs
    // before anything is removed so the selection and the register both survive a 0 p untouched.
    String pasted = value.repeat(CountPrefix.countOrOne(emc));
    if (pasted.isEmpty()) return;
    String content = emc.document().getContent();
    int start = Math.min(emc.session().getCursorQuantumStart(), content.length());
    int end = Math.min(Math.max(emc.session().getCursorQuantumEnd(), start), content.length());
    String displaced = content.substring(start, end);
    emc.document().putContent(content.substring(0, start) + pasted + content.substring(end));
    // The register records what this keypress removed, and only then.
    if (!displaced.isEmpty()) {
      emc.session().putGlobalVar("yankRegister", ObjectVisurVar.make(displaced));
      // The text a paste covered goes out too, which is the map's rule even when it means the clipboard ends up holding
      // something the user never copied: a register you paste from that is not the register you paste into is worse.
      Clipboard.offer(emc.session(), displaced);
    }
    // Paste finishes at span 0 with the caret on the last pasted character, so the next p — or the next d — meets the
    // text the paste left, not the selection the first one consumed. The span is s's to set, and this is the one
    // command whose post-condition is a span of zero rather than a range that happened to come back empty.
    int cursor = start + pasted.length() - 1;
    emc.session().putCA(cursor);
    int[] bounds = emc.session().getCursorQuantum().getBoundaries(cursor, emc.document().getNextLineIndices(), 0, false);
    emc.session().putCursorQuantumStartAndScroll(bounds[0]);
    emc.session().putCursorQuantumEndAndScroll(bounds[1]);
    emc.session().putSpan(0);
    emc.session().putIsAtQuantumStart(true);
  }
}
