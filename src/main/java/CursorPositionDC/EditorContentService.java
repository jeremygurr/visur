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

  public int[] calcLongLineBoundaries(int realCA, ArrayList<Integer> newlineIndices, int span, boolean includeTail, LocalMap<EditorModelKey, Object> editorModel) {
    LineQuantum lineQuantum = new LineQuantum();
    return lineQuantum.getBoundaries(realCA, newlineIndices, span, false);
  }

  /**
   * Where every line in the document starts, which is a property of the content and of nothing else — no window, no
   * width. Recomputed whenever the content changes, which is the only thing that can change it.
   */
  public ArrayList<Integer> getNextLineIndices(LocalMap<EditorModelKey, Object> editorModel) {
    return (ArrayList<Integer>) newlineIndicesCell(editorModel).getVal();
  }

  /** One cell for the whole document: every window's cursor derives its row and column from these offsets. */
  public void putNewlineIndicesCell(BrickVisurVar cell, LocalMap<EditorModelKey, Object> editorModel) {
    editorModel.put(newlineIndices, new NewlineIndicesCell(cell));
  }

  private BrickVisurVar newlineIndicesCell(LocalMap<EditorModelKey, Object> editorModel) {
    return ((NewlineIndicesCell) editorModel.get(newlineIndices)).cell();
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

  public void initializeEditorContent(String contentLines, LocalMap<EditorModelKey, Object> editorModel) {
    editorModel.put(editorContent, contentLines);
  }

  public void putEditorContent(String contentLines, LocalMap<EditorModelKey, Object> editorModel) {
    editorModel.put(editorContent, contentLines);
    updateNextLineIndices(editorModel);
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
    newlineIndicesCell(editorModel).putVal(indices);
  }


}
