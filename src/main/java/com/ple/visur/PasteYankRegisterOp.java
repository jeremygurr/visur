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
    if (displaced.isEmpty()) {
      // Over nothing there is no span to keep, and the caret goes on the last pasted character so the next p — or the
      // next d — meets the text the paste left. This is the command whose post-condition is a span of zero rather than
      // a range that happened to come back empty, and it is zero because nothing was selected to begin with.
      int cursor = start + pasted.length() - 1;
      emc.session().putCA(cursor);
      emc.session().putSpan(0);
      int[] bounds = emc.session().getCursorQuantum().getBoundaries(cursor, emc.document().getNextLineIndices(), 0, false);
      emc.session().putCursorQuantumStartAndScroll(bounds[0]);
      emc.session().putCursorQuantumEndAndScroll(bounds[1]);
      emc.session().putIsAtQuantumStart(true);
      return;
    }
    // What was selected is still selected, and what is selected is now the text that was pasted. So the span's quantum
    // gives way to the character: it is the only quantum whose unit can end where a paste ends. An "X" pasted over
    // "one " leaves "Xtwo three" behind, and a word quantum here selects "Xtwo" — three characters pasted over by
    // nobody, highlighted, and the next d would take them. A span that paints one thing and deletes another is the
    // bug this operator was written against, so exactness wins over the quantum the selection arrived with.
    emc.session().putCursorQuantum(emc.getQuantumNameToQuantum().get("character"));
    emc.session().putCA(start); // a window with a span begins at its start, and the cursor marks that
    emc.session().putSpan(pasted.length());
    emc.session().putCursorQuantumStartAndScroll(start);
    emc.session().putCursorQuantumEndAndScroll(start + pasted.length());
    emc.session().putIsAtQuantumStart(true);
  }
}
