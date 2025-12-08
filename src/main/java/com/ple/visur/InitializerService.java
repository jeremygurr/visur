package com.ple.visur;

import CursorPositionDC.*;
import DataClass.CompoundDataClassBrick;
import DataClass.LayeredDataClassBrick;
import DataClass.PrimitiveDataClassBrick;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Stack;

import static com.ple.visur.EditorMode.navigate;
import static com.ple.visur.EditorModelKey.globalVariableMap;

public class InitializerService {

  private final EditorModelCoupler emc;

  public static InitializerService make(EditorModelCoupler editorModelCoupler) {
    return new InitializerService(editorModelCoupler);
  }

  private InitializerService(EditorModelCoupler editorModelCoupler) {
    this.emc = editorModelCoupler;
    initializeEditorModel();
  }

  public void initializeEditorModel() {
    VariableMap initialGvm = new VariableMap(new HashMap<>());
    emc.editorModel.put(globalVariableMap, initialGvm);

    emc.putEditorMode(navigate);

    Stack<EditorSubmode> editorSubmodeStack = new Stack<>();
    editorSubmodeStack.push(EditorSubmode.navigate);
    emc.putEditorSubmodeStack(editorSubmodeStack);

    emc.putExecutionDataStack(new ExecutionDataStack());

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

    initializeCursorPositionDCBsAndBVVs();

    emc.initializeEditorContent(initialEditorContent);
    emc.updateNextLineIndices();
    emc.putLineWrapping(LineWrapping.wrapped);

    emc.putCA(0);
    emc.putVirtualCX(0);
    emc.putCanvasStart(0);
    emc.putFY(0);

    emc.putIsInCommandState(false);
    emc.putCommandStateContent("");
    emc.putCommandCursor(emc.getCommandStateContent().length());
    emc.putPreviousSearchTarget("");
    if(emc.getEditorContent().length() > 0) {
      emc.putSpan(1);
    } else {
      emc.putSpan(0);
    }

    initializeQuantums();

    initializeKeymaps();

  }

  private void initializeCursorPositionDCBsAndBVVs() {
    CursorPositionDCHolder cursorPositionDCHolder = new CursorPositionDCHolder();
    WholeNumberListDC wholeNumberListDC = cursorPositionDCHolder.wholeNumberListDC;
    PrimitiveDataClassBrick nlDCB = wholeNumberListDC.makeBrick("nl", new ArrayList<>(), true);
    CursorPositionDC cursorPositionDC = cursorPositionDCHolder.cursorPositionDC;
    LayeredDataClassBrick cursorPositionDCB = cursorPositionDC.makeBrick(nlDCB);
    CompoundDataClassBrick coordinatesDCB = cursorPositionDCB.getLayer(0);
    CompoundDataClassBrick caAndNLDCB = (CompoundDataClassBrick) coordinatesDCB.getInner("caAndNL");
    CompoundDataClassBrick cxcyAndNLDCB = (CompoundDataClassBrick) coordinatesDCB.getInner("cxcyAndNL");
    PrimitiveDataClassBrick cxDCB = (PrimitiveDataClassBrick) cxcyAndNLDCB.getInner("cx");
    PrimitiveDataClassBrick cyDCB = (PrimitiveDataClassBrick) cxcyAndNLDCB.getInner("cy");
    PrimitiveDataClassBrick caDCB = (PrimitiveDataClassBrick) caAndNLDCB.getInner("ca");
    caAndNLDCB.putInner("ca", caDCB);
    caAndNLDCB.putInner("nl", nlDCB);
    cxcyAndNLDCB.putInner("cx", cxDCB);
    cxcyAndNLDCB.putInner("cy", cyDCB);
    cxcyAndNLDCB.putInner("nl", nlDCB);
    coordinatesDCB.putInner("caAndNL", caAndNLDCB);
    coordinatesDCB.putInner("cxcyAndNL", cxcyAndNLDCB);

    initializeBVVs(nlDCB, caDCB, cxDCB, cyDCB);

  }

  private void initializeBVVs(PrimitiveDataClassBrick nlDCB, PrimitiveDataClassBrick caDCB, PrimitiveDataClassBrick cxDCB, PrimitiveDataClassBrick cyDCB) {
    BrickVisurVar nlBVV = BrickVisurVar.make(nlDCB);
    BrickVisurVar caBVV = BrickVisurVar.make(caDCB);
    BrickVisurVar cxBVV = BrickVisurVar.make(cxDCB);
    BrickVisurVar cyBVV = BrickVisurVar.make(cyDCB);
    emc.putGlobalVar("nl", nlBVV);
    emc.putGlobalVar("ca", caBVV);
    emc.putGlobalVar("cx", cxBVV);
    emc.putGlobalVar("cy", cyBVV);
  }

  private void initializeQuantums() {
    QuantumNameToQuantum quantumNameToQuantum = new QuantumNameToQuantum();
    String startingCursorQuantumName = "character";
    String startingScopeQuantumName = "line";
    quantumNameToQuantum.put("word", new RegexQuantum("word", "\\S+"));
    quantumNameToQuantum.put("character", new CharacterQuantum());
    quantumNameToQuantum.put("line", new LineQuantum());
    quantumNameToQuantum.put("document", new DocumentQuantum());
    emc.putQuantumNameToQuantum(quantumNameToQuantum);

    KeyToQuantumName keyToQuantumName = new KeyToQuantumName();
    keyToQuantumName.put("q", "character");
    keyToQuantumName.put("w", "word");
    keyToQuantumName.put("e", "line");
    keyToQuantumName.put("r", "document");
    emc.putKeyToQuantumName(keyToQuantumName);

    emc.putCursorQuantum(emc.getQuantumNameToQuantum().get(startingCursorQuantumName));
    emc.putScopeQuantum(emc.getQuantumNameToQuantum().get(startingScopeQuantumName));
    int realCA = emc.getCA();
    int bounds[] = emc.getQuantumNameToQuantum().get(startingCursorQuantumName).getBoundaries(realCA, emc.getNextLineIndices(), emc.getSpan(), false);
    emc.putCursorQuantumStartAndScroll(bounds[0]);
    emc.putCursorQuantumEnd(bounds[1]);
    emc.putIsAtQuantumStart(true);
    System.out.println("start bound = " + bounds[0]);
    System.out.println("end bound = " + bounds[1]);
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

    Keymap spanKeymap = Keymap.make("span");
    spanKeymap = initializeSpanKeymap(spanKeymap);
    keymapMap.put(EditorSubmode.span, spanKeymap);

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
      scs.compile("\"span\" pushSubmode")
    );
    keymap.put(KeyPressed.from("i"),
      scs.compile("\"span\" \"tempSpan\" -> \"cursorQuantum\" \"tempCursorQuantum\" -> 0 setSpan \"character\" quantumStart \"insert\" changeMode \"character\" changeCursorQuantum")
    );
    keymap.put(KeyPressed.from("a"),
      scs.compile("\"span\" \"tempSpan\" -> \"cursorQuantum\" \"tempCursorQuantum\" -> 0 setSpan \"character\" quantumEnd \"insert\" changeMode \"character\" changeCursorQuantum")
    );
    keymap.put(KeyPressed.from("o"),
      scs.compile("\"span\" \"tempSpan\" -> \"cursorQuantum\" \"tempCursorQuantum\" -> \"replace\" changeMode 0 setSpan \"character\" changeCursorQuantum")
    );
    keymap.put(KeyPressed.from("d"),
      scs.compile("deleteCursorQuantum")
    );
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

  private Keymap initializeSpanKeymap(Keymap spanKeymap) {
    final KeymapHandler[] handlers = new KeymapHandler[1];
    handlers[0] = SpanSubmodeHandler.make();
    spanKeymap.putHandlers(handlers);
    return spanKeymap;
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
      scs.compile("\"tempSpan\" \"span\" -> \"tempCursorQuantum\" changeCursorQuantum \"tempSpan\" removeGlobalVar \"tempCursorQuantum\" removeGlobalVar \"navigate\" changeMode")
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
      scs.compile("\"tempSpan\" \"span\" -> \"tempCursorQuantum\" changeCursorQuantum \"tempSpan\" removeGlobalVar \"tempCursorQuantum\" removeGlobalVar \"navigate\" changeMode")
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
