package com.ple.visur;

import CursorPositionDC.*;
import DataClass.CompoundDataClassBrick;
import DataClass.LayeredDataClassBrick;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Stack;

import static com.ple.visur.EditorMode.navigate;

public class InitializerService {

  private final EditorModelCoupler emc;
  private final String startingCursorQuantumName = "character";
  private final String startingScopeQuantumName = "line";

  public static InitializerService make(EditorModelCoupler editorModelCoupler) {
    return new InitializerService(editorModelCoupler);
  }

  private InitializerService(EditorModelCoupler editorModelCoupler) {
    this.emc = editorModelCoupler;
    initializeEditorModel();
  }

  public void initializeEditorModel() {
    emc.session().openFresh();

    final String initialEditorContent = "The unanimous Declaration of the thirteen united States of America, When in the Course of human events, it becomes necessary for one people to dissolve the political bands which have connected them with another, and to assume among the powers of the earth, the separate and equal station to which the Laws of Nature and of Nature’s God entitle them, a decent respect to the opinions of mankind requires that they should declare the causes which impel them to the separation.\n" +
      "\n" +
      "We hold these truths to be self-evident, that all men are created equal, that they are endowed by their Creator with certain unalienable Rights, that among these are Life, Liberty and the pursuit of Happiness.–That to secure these rights, Governments are instituted among Men, deriving their just powers from the consent of the governed, –That whenever any Form of Government becomes destructive of these ends, it is the Right of the People to alter or to abolish it, and to institute new Government, laying its foundation on such principles and organizing its powers in such form, as to them shall seem most likely to effect their Safety and Happiness. Prudence, indeed, will dictate that Governments long established should not be changed for light and transient causes; and accordingly all experience hath shewn, that mankind are more disposed to suffer, while evils are sufferable, than to right themselves by abolishing the forms to which they are accustomed. But when a long train of abuses and usurpations, pursuing invariably the same Object evinces a design to reduce them under absolute Despotism, it is their right, it is their duty, to throw off such Government, and to provide new Guards for their future security.–Such has been the patient sufferance of these Colonies; and such is now the necessity which constrains them to alter their former Systems of Government. The history of the present King of Great Britain is a history of repeated injuries and usurpations, all having in direct object the establishment of an absolute Tyranny over these States. To prove this, let Facts be submitted to a candid world.\n" +
      "\n" +
      "He has refused his Assent to Laws, the most wholesome and necessary for the public good.\n" +
      "\n" +
      "He has forbidden his Governors to pass Laws of immediate and pressing importance, unless suspended in their operation till his Assent should be obtained; and when so suspended, he has utterly neglected to attend to them.\n" +
      "\n" +
      "He has refused to pass other Laws for the accommodation of large districts of people, unless those people would relinquish the right of Representation in the Legislature, a right inestimable to them and formidable to tyrants only.\n" +
      "\n" +
      "He has called together legislative bodies at places unusual, uncomfortable, and distant from the depository of their public Records, for the sole purpose of fatiguing them into compliance with his measures.\n" +
      "\n" +
      "He has dissolved Representative Houses repeatedly, for opposing with manly firmness his invasions on the rights of the people.\n" +
      "\n" +
      "He has refused for a long time, after such dissolutions, to cause others to be elected; whereby the Legislative powers, incapable of Annihilation, have returned to the People at large for their exercise; the State remaining in the mean time exposed to all the dangers of invasion from without, and convulsions within.\n" +
      "\n" +
      "He has endeavoured to prevent the population of these States; for that purpose obstructing the Laws for Naturalization of Foreigners; refusing to pass others to encourage their migrations hither, and raising the conditions of new Appropriations of Lands.\n" +
      "\n" +
      "He has obstructed the Administration of Justice, by refusing his Assent to Laws for establishing Judiciary powers.\n" +
      "\n" +
      "He has made Judges dependent on his Will alone, for the tenure of their offices, and the amount and payment of their salaries. He has erected a multitude of New Offices, and sent hither swarms of Officers to harrass our people, and eat out their substance.\n" +
      "\n" +
      "He has kept among us, in times of peace, Standing Armies without the Consent of our legislatures.\n" +
      "\n" +
      "He has affected to render the Military independent of and superior to the Civil power.\n" +
      "\n" +
      "He has combined with others to subject us to a jurisdiction foreign to our constitution, and unacknowledged by our laws; giving his Assent to their Acts of pretended Legislation:\n" +
      "\n" +
      "For Quartering large bodies of armed troops among us:\n" +
      "\n" +
      "For protecting them, by a mock Trial, from punishment for any Murders which they should commit on the Inhabitants of these States:\n" +
      "\n" +
      "For cutting off our Trade with all parts of the world: For imposing Taxes on us without our Consent:\n" +
      "\n" +
      "For depriving us in many cases, of the benefits of Trial by Jury:\n" +
      "\n" +
      "For transporting us beyond Seas to be tried for pretended offences\n" +
      "\n" +
      "For abolishing the free System of English Laws in a neighbouring Province, establishing therein an Arbitrary government, and enlarging its Boundaries so as to render it at once an example and fit instrument for introducing the same absolute rule into these Colonies:\n" +
      "\n" +
      "For taking away our Charters, abolishing our most valuable Laws, and altering fundamentally the Forms of our Governments:\n" +
      "\n" +
      "For suspending our own Legislatures, and declaring themselves invested with power to legislate for us in all cases whatsoever.\n" +
      "\n" +
      "He has abdicated Government here, by declaring us out of his Protection and waging War against us.\n" +
      "\n" +
      "He has plundered our seas, ravaged our Coasts, burnt our towns, and destroyed the lives of our people.\n" +
      "\n" +
      "He is at this time transporting large Armies of foreign Mercenaries to compleat the works of death, desolation and tyranny, already begun with circumstances of Cruelty & perfidy scarcely paralleled in the most barbarous ages, and totally unworthy the Head of a civilized nation.\n" +
      "\n" +
      "He has constrained our fellow Citizens taken Captive on the high Seas to bear Arms against their Country, to become the executioners of their friends and Brethren, or to fall themselves by their Hands.\n" +
      "\n" +
      "He has excited domestic insurrections amongst us, and has endeavoured to bring on the inhabitants of our frontiers, the merciless Indian Savages, whose known rule of warfare, is an undistinguished destruction of all ages, sexes and conditions.\n" +
      "\n";

    String content = startupContent();
    if (emc.document().getSourceFilePath() != null) {
      // The text came off that file, so it is not unsaved work. Without a startup file the content is either empty or
      // a benchmark seed, and seed text that has never been written anywhere earns the marker like any other typing.
      emc.document().putContentAsSaved(content);
    } else {
      emc.document().putContent(content);
    }
    emc.document().updateNextLineIndices();
    emc.session().putVirtualCX(0);
    emc.session().putCanvasStart(0);
    emc.session().putFY(0);

    emc.session().putIsInCommandState(false);
    emc.session().putCommandStateContent("");
    emc.session().putCommandCursor(emc.session().getCommandStateContent().length());
    emc.session().putPreviousSearchTarget("");

    initializeQuantumRegistry();

    // After the registry, because a window starts on a quantum of the document and that is what the registry names.
    startSession(emc.session());

    initializeKeymaps();

  }


  private void initializeQuantumRegistry() {
    EditorSpec editorSpec = EditorSpec.plaintext();
    emc.putQuantumNameToQuantum(editorSpec.registry());

    KeyToQuantumName keyToQuantumName = new KeyToQuantumName();
    keyToQuantumName.put("q", "character");
    keyToQuantumName.put("w", "word");
    keyToQuantumName.put("e", "line");
    keyToQuantumName.put("r", "document");
    editorSpec.validate(keyToQuantumName);
    emc.putKeyToQuantumName(keyToQuantumName);
  }

  /**
   * Lets a run start with a large document, so the latency bar can be measured at a stated document size.
   *
   * <p>Nothing in Visur can load a file, and the built-in content is a few kilobytes, so without this a
   * measurement at 5 MB is not possible. It is a measurement hook, not a feature: unset means the Declaration,
   * and a malformed value fails loudly rather than quietly benchmarking the wrong document.
   */
  private static String contentForThisRun(String fallback) {
    String requested = System.getenv("VISUR_CONTENT_BYTES");
    if (requested == null || requested.isBlank()) {
      return fallback;
    }
    int bytes;
    try {
      bytes = Integer.parseInt(requested.trim());
    } catch (NumberFormatException notANumber) {
      throw new IllegalArgumentException(
        "VISUR_CONTENT_BYTES must be a number of bytes, but was \"" + requested + "\"", notANumber);
    }
    String content = SizedContent.of(bytes);
    System.out.println("VISUR_CONTENT_BYTES=" + requested + " -> " + content.length() + " character document");
    return content;
  }

  /**
   * Returns the startup content for this run.
   *
   * <p>If {@code VISUR_FILE} is set, loads from that file (empty buffer if missing - vim new-file).
   * If {@code VISUR_CONTENT_BYTES} is set, uses that (large-doc benchmark).
   * If neither is set, starts with an empty document (empty, unnamed).
   */
  private String startupContent() {
    String filePath = startupFile();
    if (filePath != null) {
      // VISUR_FILE is set: load from file (empty buffer if missing)
      String content = readStartupFile(filePath);
      emc.document().setSourceFilePath(filePath);
      return content;
    }
    // No VISUR_FILE: fall through to VISUR_CONTENT_BYTES or empty.
    String content = contentForThisRun("");
    // No source file: document is unnamed.
    return content;
  }

  /**
   * Returns the startup file path if {@code VISUR_FILE} is set, {@code null} otherwise.
   */
  private String startupFile() {
    // The property first, as with `visur.port`: a test cannot change the environment of the process it is already in,
    // and the only alternative was a test that starts a JVM to boot the thing properly.
    String filePath = System.getProperty("visur.file");
    if (filePath == null || filePath.isBlank()) {
      filePath = System.getenv("VISUR_FILE");
    }
    return (filePath == null || filePath.isBlank()) ? null : filePath;
  }

  /**
   * Reads the startup file if it exists. If the file is missing, starts with
   * an empty buffer (vim's new-file behaviour). If the file exists but is
   * unreadable or contains invalid UTF-8, fails loudly.
   */
  private String readStartupFile(String filePath) {
    try {
      Path path = Paths.get(filePath);
      if (!Files.exists(path)) {
        // Missing file is not an error: start with empty buffer (vim new-file).
        // The file will be created at first save.
        System.out.println("VISUR_FILE path does not exist yet: " + filePath + " (empty document, will be created at first save)");
        return "";
      }
      if (!Files.isRegularFile(path)) {
        throw new IllegalStateException("VISUR_FILE points to a non-regular file: " + filePath);
      }
      byte[] bytes = Files.readAllBytes(path);
      String content = new String(bytes, StandardCharsets.UTF_8);
      System.out.println("Loaded startup file: " + filePath + " (" + bytes.length + " bytes)");
      return content;
    } catch (IOException e) {
      throw new IllegalStateException("Cannot read VISUR_FILE (" + filePath + "): " + e.getMessage(), e);
    }
  }

  /**
   * A window that has just been opened, whether it is the one the server booted with or a socket that arrived while
   * it was running: it starts on the quantum the editor starts on, with its own cursor and its own variables.
   *
   * <p>Everything window-shaped that boot used to do to the single shared model belongs here, because a second client
   * is a second window and starts where a first one started.
   */

  public void startSession(Session session) {
    session.installCursorCells(emc.document().lineOffsetsCell());
    session.putSpan(emc.document().getContent().length() > 0 ? 1 : 0);
    session.putCursorQuantum(emc.getQuantumNameToQuantum().get(startingCursorQuantumName));
    session.putScopeQuantum(emc.getQuantumNameToQuantum().get(startingScopeQuantumName));
    int bounds[] = emc.getQuantumNameToQuantum().get(startingCursorQuantumName)
      .getBoundaries(session.getCA(), emc.document().getNextLineIndices(), session.getSpan(), false);
    emc.session().putCursorQuantumStartAndScroll(bounds[0]);
    emc.session().putCursorQuantumEnd(bounds[1]);
    emc.session().putIsAtQuantumStart(true);
  }

  private void initializeKeymaps() {
    KeymapMap keymapMap = KeymapMap.make();

    Keymap navigateKeymap = Keymap.make("navigate");
    navigateKeymap = initializeNavigateKeymap(navigateKeymap);
    keymapMap.put(EditorSubmode.navigate, navigateKeymap);

    Keymap insertKeymap = Keymap.make("insert");
    insertKeymap = initializeInsertKeymap(insertKeymap);
    keymapMap.put(EditorSubmode.insert, insertKeymap);

    Keymap replaceKeymap = Keymap.make("replace");
    replaceKeymap = initializeReplaceKeymap(replaceKeymap);
    keymapMap.put(EditorSubmode.replace, replaceKeymap);

    Keymap quantumStartKeymap = Keymap.make("quantumStart");
    quantumStartKeymap = initializeQuantumStartKeymap(quantumStartKeymap);
    keymapMap.put(EditorSubmode.quantumStart, quantumStartKeymap);

    Keymap quantumEndKeymap = Keymap.make("quantumEnd");
    quantumEndKeymap = initializeQuantumEndKeymap(quantumEndKeymap);
    keymapMap.put(EditorSubmode.quantumEnd, quantumEndKeymap);

    Keymap searchForwardKeymap = Keymap.make("searchForward");
    searchForwardKeymap = initializeSearchForwardKeymap(searchForwardKeymap);
    keymapMap.put(EditorSubmode.searchForward, searchForwardKeymap);

    Keymap searchBackwardKeymap = Keymap.make("searchBackward");
    searchBackwardKeymap = initializeSearchBackwardKeymap(searchBackwardKeymap);
    keymapMap.put(EditorSubmode.searchBackward, searchBackwardKeymap);

    Keymap changeScopeKeymap = Keymap.make("changeScope");
    changeScopeKeymap = initializeChangeScopeKeymap(changeScopeKeymap);
    keymapMap.put(EditorSubmode.changeScope, changeScopeKeymap);

    emc.putKeymapMap(keymapMap);

  }

  private Keymap initializeNavigateKeymap(Keymap keymap) {
    CommandCompileService scs = ServiceHolder.commandCompileService;
    keymap.put(KeyPressed.from("h"),
      scs.compile("-1 0 relativeMove")
    );
    keymap.put(KeyPressed.from("l"),
      scs.compile("1 0 relativeMove")
    );
    keymap.put(KeyPressed.from("j"),
      scs.compile("0 1 relativeMove")
    );
    keymap.put(KeyPressed.from("k"),
      scs.compile("0 -1 relativeMove")
    );
    keymap.put(KeyPressed.from("ArrowLeft"),
      scs.compile("-1 0 relativeMove")
    );
    keymap.put(KeyPressed.from("ArrowRight"),
      scs.compile("1 0 relativeMove")
    );
    keymap.put(KeyPressed.from("ArrowDown"),
      scs.compile("0 1 relativeMove")
    );
    keymap.put(KeyPressed.from("ArrowUp"),
      scs.compile("0 -1 relativeMove")
    );
    keymap.put(KeyPressed.from("["),
      scs.compile("\"quantumStart\" pushSubmode")
    );
    keymap.put(KeyPressed.from("]"),
      scs.compile("\"quantumEnd\" pushSubmode")
    );
    keymap.put(KeyPressed.from("s"),
      scs.compile("setSpan") // the count, if any, was typed before it: "12 s"
    );
    keymap.put(KeyPressed.from("\\"),
      scs.compile("toggleSeparators")
    );
    keymap.put(KeyPressed.from("i"),
      scs.compile("\"span\" \"tempSpan\" -> \"cursorQuantum\" \"tempCursorQuantum\" -> stashCount 0 setSpan \"character\" quantumStart \"insert\" changeMode \"character\" changeCursorQuantum")
    );
    keymap.put(KeyPressed.from("a"),
      scs.compile("\"span\" \"tempSpan\" -> \"cursorQuantum\" \"tempCursorQuantum\" -> stashCount 0 setSpan \"character\" quantumEnd \"insert\" changeMode \"character\" changeCursorQuantum")
    );
    keymap.put(KeyPressed.from("o"),
      scs.compile("\"span\" \"tempSpan\" -> \"cursorQuantum\" \"tempCursorQuantum\" -> stashCount \"replace\" changeMode 0 setSpan \"character\" changeCursorQuantum")
    );
    // The caret comes from the operator rather than from `quantumStart`, which would put it back at the selection the
    // line opened in front of. Everything else is what `i` and `a` do.
    keymap.put(KeyPressed.from("g"),
      scs.compile("\"span\" \"tempSpan\" -> \"cursorQuantum\" \"tempCursorQuantum\" -> stashCountNewlineSeparated openLineAbove 0 setSpan \"insert\" changeMode \"character\" changeCursorQuantum")
    );
    keymap.put(KeyPressed.from("G"),
      scs.compile("\"span\" \"tempSpan\" -> \"cursorQuantum\" \"tempCursorQuantum\" -> stashCountNewlineSeparated openLineBelow 0 setSpan \"insert\" changeMode \"character\" changeCursorQuantum")
    );
    keymap.put(KeyPressed.from("d"),
      scs.compile("deleteCursorQuantum")
    );
    // One command per u, the same unit a snapshot was taken at. R rather than Ctrl-r: the browser sends Ctrl-r as "r"
    // and reloads the page with it, and the terminal drops chords on purpose.
    keymap.put(KeyPressed.from("u"), scs.compile("undo"));
    keymap.put(KeyPressed.from("R"), scs.compile("redo"));
    keymap.put(KeyPressed.from("ZZ"),
      scs.compile("saveFile")
    );
    keymap.put(KeyPressed.from("y"), scs.compile("yankSpan"));
    keymap.put(KeyPressed.from("p"), scs.compile("pasteYankRegister"));
    keymap.put(KeyPressed.from("P"), scs.compile("pasteBeforeYankRegister"));
    keymap.put(KeyPressed.from("f"),
      scs.compile("clearEDS \"searchForward\" pushSubmode")
    );
    keymap.put(KeyPressed.from("F"),
      scs.compile("clearEDS \"searchBackward\" pushSubmode")
    );
    keymap.put(KeyPressed.from("n"),
      scs.compile("previousSearchTarget searchInPreviousDirection")
    );
    keymap.put(KeyPressed.from("N"),
      scs.compile("previousSearchTarget searchNotInPreviousDirection")
    );
    keymap.put(KeyPressed.from("/"),
      scs.compile("\"changeScope\" pushSubmode")
    );
    final KeymapHandler[] handlers = new KeymapHandler[1];
    handlers[0] = ChangeCursorQuantumHandler.make();
    keymap.putHandlers(handlers);
    return keymap;
  }

  private Keymap initializeQuantumStartKeymap(Keymap quantumStartKeymap) {
    final KeymapHandler[] handlers = new KeymapHandler[1];
    handlers[0] = QuantumStartSubmodeHandler.make();
    quantumStartKeymap.putHandlers(handlers);
    return quantumStartKeymap;
  }

  private Keymap initializeQuantumEndKeymap(Keymap quantumEndKeymap) {
    final KeymapHandler[] handlers = new KeymapHandler[1];
    handlers[0] = QuantumEndSubmodeHandler.make();
    quantumEndKeymap.putHandlers(handlers);
    return quantumEndKeymap;
  }

  private Keymap initializeSearchForwardKeymap(Keymap searchForwardKeymap) {
    final KeymapHandler[] handlers = new KeymapHandler[1];
    handlers[0] = SearchForwardSubmodeHandler.make();
    searchForwardKeymap.putHandlers(handlers);
    return searchForwardKeymap;
  }

  private Keymap initializeSearchBackwardKeymap(Keymap searchBackwardKeymap) {
    final KeymapHandler[] handlers = new KeymapHandler[1];
    handlers[0] = SearchBackwardSubmodeHandler.make();
    searchBackwardKeymap.putHandlers(handlers);
    return searchBackwardKeymap;
  }

  private Keymap initializeInsertKeymap(Keymap insertKeymap) {
    CommandCompileService scs = ServiceHolder.commandCompileService;
    insertKeymap.put(KeyPressed.from("Escape"),
      scs.compile("\"tempSpan\" \"span\" -> \"tempCursorQuantum\" changeCursorQuantum \"tempSpan\" removeGlobalVar \"tempCursorQuantum\" removeGlobalVar repeatSessionText \"navigate\" changeMode")
    );
    insertKeymap.put(KeyPressed.from("ArrowLeft"),
      scs.compile("-1 0 relativeMove")
    );
    insertKeymap.put(KeyPressed.from("ArrowRight"),
      scs.compile("1 0 relativeMove")
    );
    insertKeymap.put(KeyPressed.from("ArrowUp"),
      scs.compile("0 -1 relativeMove")
    );
    insertKeymap.put(KeyPressed.from("ArrowDown"),
      scs.compile("0 1 relativeMove")
    );
    insertKeymap.put(KeyPressed.from("Backspace"),
      scs.compile("deletePreviousChar -1 0 relativeMove")
    );
    insertKeymap.put(KeyPressed.from("Enter"),
      scs.compile("insertNewline")
    );
    final KeymapHandler[] insertKeymapHandlers = new KeymapHandler[1];
    insertKeymapHandlers[0] = InsertModeHandler.make();
    insertKeymap.putHandlers(insertKeymapHandlers);

    return insertKeymap;
  }

  private Keymap initializeReplaceKeymap(Keymap replaceKeymap) {
    CommandCompileService scs = ServiceHolder.commandCompileService;
    replaceKeymap.put(KeyPressed.from("Escape"),
      scs.compile("\"tempSpan\" \"span\" -> \"tempCursorQuantum\" changeCursorQuantum \"tempSpan\" removeGlobalVar \"tempCursorQuantum\" removeGlobalVar repeatSessionText \"navigate\" changeMode")
    );
    replaceKeymap.put(KeyPressed.from("ArrowLeft"),
      scs.compile("-1 0 relativeMove")
    );
    replaceKeymap.put(KeyPressed.from("ArrowRight"),
      scs.compile("1 0 relativeMove")
    );
    replaceKeymap.put(KeyPressed.from("ArrowUp"),
      scs.compile("0 -1 relativeMove")
    );
    replaceKeymap.put(KeyPressed.from("ArrowDown"),
      scs.compile("0 1 relativeMove")
    );
    replaceKeymap.put(KeyPressed.from("Backspace"),
      scs.compile("-1 0 relativeMove")
    );
    final KeymapHandler[] replaceKeymapHandlers = new KeymapHandler[1];
    replaceKeymapHandlers[0] = ReplaceModeHandler.make();
    replaceKeymap.putHandlers(replaceKeymapHandlers);

    return replaceKeymap;
  }

  private Keymap initializeChangeScopeKeymap(Keymap changeScopeKeymap) {
    final KeymapHandler[] changeScopeHandlers = new KeymapHandler[1];
    changeScopeHandlers[0] = ChangeScopeSubmodeHandler.make();
    changeScopeKeymap.putHandlers(changeScopeHandlers);

    return changeScopeKeymap;
  }

}
