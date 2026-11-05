package com.ple.visur;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class NativeOpWord implements Word {

  @Override
  public CompiledWordResponse compile(String sentence) {
    Pattern pattern = Pattern.compile("([^\\s]+)(.*)");
    Matcher matcher = pattern.matcher(sentence);
    if(matcher.matches()) {
      String opSource = matcher.group(1);
      CompiledWordResponse cwr;
      Operator op = null;
      switch (opSource) {
        case "absoluteMove":
          op = new AbsoluteMoveOp();
          break;
        case "relativeMove":
          op = new RelativeMoveOp();
          break;
        case "clearEDS":
          op = new ClearEDSOp();
          break;
        case "pushSubmode":
          op = new PushSubmodeOp();
          break;
        case "removeSubmode":
          op = new RemoveSubmodeOp();
          break;
        case "changeCursorQuantum":
          op = new ChangeCursorQuantumOp();
          break;
        case "+":
          op = new AddOperator();
          break;
        case "quantumStart":
          op = new QuantumStartOp();
          break;
        case "quantumEnd":
          op = new QuantumEndOp();
          break;
        case "setSpan":
          op = new SetSpanOp();
          break;
        case "toggleSeparators":
          op = new ToggleSeparatorsOp();
          break;
        case "changeMode":
          op = new ChangeModeOp();
          break;
        case "insertChar":
          op = new InsertCharOp();
          break;
        case "insertNewline":
          op = new InsertNewlineOp();
          break;
        case "deletePreviousChar":
          op = new DeletePreviousCharOp();
          break;
        case "removeGlobalVar":
          op = new RemoveGlobalVarOp();
          break;
        case "deleteCursorQuantum":
          op = new DeleteCursorQuantumOp();
          break;
        case "yankSpan":
          op = new YankSpanOp();
          break;
        case "pasteYankRegister":
          op = new PasteYankRegisterOp();
          break;
        case "replaceChar":
          op = new ReplaceCharOp();
          break;
        case "changeScope":
          op = new ChangeScopeOp();
          break;
        case "removeLastCharInSearchTarget":
          op = new RemoveLastCharInSearchTargetOp();
          break;
        case "searchForward":
          op = new SearchForwardOp();
          break;
        case "searchBackward":
          op = new SearchBackwardOp();
          break;
        case "searchInPreviousDirection":
          op = new SearchInPreviousDirectionOp();
          break;
        case "searchNotInPreviousDirection":
          op = new SearchNotInPreviousDirectionOp();
          break;
        case "saveFile":
          op = new SaveFileOp();
          break;
        case "loadFile":
          op = new LoadFileOp();
          break;
        default:
          System.out.println("operator " + opSource + " not recognized.");
      }
      if(op != null) {
        cwr = new CompiledWordResponse(op, null, matcher.group(2));
      } else {
        cwr = null;
      }
      return cwr;
    } else {
      return null;
    }
  }
}
