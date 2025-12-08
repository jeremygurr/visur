package CursorPositionDC;

import com.ple.visur.*;
import io.vertx.rxjava3.core.shareddata.LocalMap;

import java.util.ArrayList;

import static com.ple.visur.EditorModelKey.*;

public class EditorContentService {

  public static EditorContentService make() {
    return new EditorContentService();
  }

  public String getEditorContent(LocalMap<EditorModelKey, Object> editorModel) {
    return (String)editorModel.get(editorContent);
  }

  public CursorPositionDCHolder getCursorPositionDCHolder(LocalMap<EditorModelKey, Object> editorModel) {
    return (CursorPositionDCHolder)editorModel.get(cursorPositionDCHolder);
  }

  public VisurVar getGlobalVar(String varName, LocalMap<EditorModelKey, Object> editorModel) {
    VariableMap globalVarMap = getGlobalVarMap(editorModel);
    return globalVarMap.get(varName);
  }

  public VariableMap getGlobalVarMap(LocalMap<EditorModelKey, Object> editorModel) {
    return (VariableMap) editorModel.get(globalVariableMap);
  }

  public int[] calcLongLineBoundaries(int realCA, ArrayList<Integer> newlineIndices, int span, boolean includeTail, LocalMap<EditorModelKey, Object> editorModel) {
    LineQuantum lineQuantum = new LineQuantum();
    return lineQuantum.getBoundaries(realCA, newlineIndices, span, false);
  }

  public ArrayList<Integer> getNextLineIndices(LocalMap<EditorModelKey, Object> editorModel) {
    BrickVisurVar nlBVV = (BrickVisurVar)getGlobalVar("nl", editorModel);
    return (ArrayList<Integer>) nlBVV.getVal();
  }

  public int calcNextNewlineIndexFromAbsPosition(int realCA, LocalMap<EditorModelKey, Object> editorModel) {
    ArrayList<Integer> newlineIndices = getNextLineIndices(editorModel);
    int nextNewlineIndex = -1;
    for(int i = 0; i < newlineIndices.size(); i++) {
      nextNewlineIndex = newlineIndices.get(i);
      if(realCA < newlineIndices.get(i)) {
        break;
      }
    }
    return nextNewlineIndex;
  }

  public int getCanvasWidth(LocalMap<EditorModelKey, Object> editorModel) {
    return (int)editorModel.get(canvasWidth);
  }

  public int getCanvasHeight(LocalMap<EditorModelKey, Object> editorModel) {
    return (int) editorModel.get(canvasHeight);
  }

  public void initializeEditorContent(String contentLines, LocalMap<EditorModelKey, Object> editorModel) {
    editorModel.put(editorContent, contentLines);
  }

  public void putEditorContent(String contentLines, LocalMap<EditorModelKey, Object> editorModel) {
    editorModel.put(editorContent, contentLines);
    updateNextLineIndices(editorModel);
  }

  public void putCursorPositionDCHolder(CursorPositionDCHolder cpDCHolder, LocalMap<EditorModelKey, Object> editorModel) {
    editorModel.put(cursorPositionDCHolder, cpDCHolder);
  }

  public void putGlobalVar(String globalVarName, VisurVar globalVarValue, LocalMap<EditorModelKey, Object> editorModel) {
    VariableMap gvm = getGlobalVarMap(editorModel);
    gvm.put(globalVarName, globalVarValue); //updates value that was previously at associated key
    putGlobalVarMap(gvm, editorModel);
  }

  public void putGlobalVarMap(VariableMap gvm, LocalMap<EditorModelKey, Object> editorModel) {
    editorModel.put(globalVariableMap, gvm);
  }

  public void updateNextLineIndices(LocalMap<EditorModelKey, Object> editorModel) {
    String content = getEditorContent(editorModel);
    ArrayList<Integer> indices = new ArrayList<>();
    for(int i = 0; i <= content.length(); i++) {
      if(i < content.length()) {
        if(content.charAt(i) == '\n') {
          indices.add(i + 1);
        }
      } else {
        indices.add(i);
      }
    }
    BrickVisurVar nlBVV = (BrickVisurVar) getGlobalVar("nl", editorModel);
    nlBVV.putVal(indices);
    putGlobalVar("nl", nlBVV, editorModel);
  }

  public void putCanvasWidth(int cw, LocalMap<EditorModelKey, Object> editorModel) {
    editorModel.put(canvasWidth, cw);
  }

  public void putCanvasHeight(int height, LocalMap<EditorModelKey, Object> editorModel) {
    editorModel.put(canvasHeight, height);
  }

}
