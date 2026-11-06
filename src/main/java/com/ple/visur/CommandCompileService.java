package com.ple.visur;

public class CommandCompileService {
  public static CommandCompileService make() {
    return new CommandCompileService();
  }

  /**
   * The {@code :} grammar: a command name, then the rest of the line.
   *
   * <p>Not the editor's sentence grammar, and not {@link #compile} with a word bolted on. Two reasons, both learned the
   * hard way:
   *
   * <ul>
   *   <li>{@code compile} cannot take a bare path. A word only compiles if some matcher claims it, so
   *       {@code loadFile /tmp/x.txt} compiled to {@code LoadFileOp} alone — the path token was never compiled, the
   *       operator ran with no argument, and every test that called the operator by hand stayed green.</li>
   *   <li>A word nothing matched was a variable recall or a silent stop, never an error. Here a word that is not a
   *       command is {@link UnknownCommandOp}, which says so on the screen.</li>
   * </ul>
   *
   * <p>The argument is the whole rest of the line, trimmed: that is what makes {@code :open ~/notes/ideas.txt} a path.
   * Quotes are still honoured, and are now only good for one thing — keeping a space, including a trailing one, which
   * an unquoted path cannot do. {@code clearEDS} heads every command that takes an argument, because a command typed
   * after another one is a fresh utterance and {@code SaveFileOp} reads its path off that same stack.
   */
  public VisurCommand compileCommandLine(String commandLine) {
    VisurCommand command = new VisurCommand(commandLine);
    String line = commandLine.strip();
    if(line.isEmpty()) {
      return command; // Enter on an empty line is nothing having been typed, not a command that failed
    }
    int endOfName = line.indexOf(' ');
    String name = endOfName < 0 ? line : line.substring(0, endOfName);
    String argument = endOfName < 0 ? "" : line.substring(endOfName + 1).strip();
    if(argument.length() >= 2 && argument.startsWith("\"") && argument.endsWith("\"")) {
      argument = argument.substring(1, argument.length() - 1);
    }

    switch(name) {
      case "w":
      case "open": {
        command.addOperatorWithInfo(new Object[] {new ClearEDSOp(), null});
        if(!argument.isEmpty()) {
          command.addOperatorWithInfo(new Object[] {new BarePathOp(), argument});
        }
        command.addOperatorWithInfo(new Object[] {
          name.equals("w") ? (Operator) new SaveFileOp() : (Operator) new LoadFileOp(), null});
        return command;
      }
      default: {
        command.addOperatorWithInfo(new Object[] {new UnknownCommandOp(), name});
        return command;
      }
    }
  }

  /** The editor's sentence grammar, which is what a keymap compiles to. Not what {@code :} takes: see
   * {@link #compileCommandLine}. */
  public VisurCommand compile(String sentence) {
    VisurCommand command = new VisurCommand(sentence);
    String currentSentence = sentence;

    LiteralNumberWord literalNumberWord = new LiteralNumberWord();
    LiteralStringWord literalStringWord = new LiteralStringWord();
    AssignmentWord assignmentWord = new AssignmentWord();
    NativeOpWord nativeOperatorWord = new NativeOpWord();
    RecallWord recallWord = new RecallWord();
    Word[] words = new Word[] {
      literalNumberWord, literalStringWord, assignmentWord, nativeOperatorWord, recallWord
    };

    compileLoop:
    while(currentSentence != "") {
      CompiledWordResponse compiledWordResponse;
      for(Word word : words) {
        compiledWordResponse = word.compile(currentSentence);
        if(compiledWordResponse != null) {
          command.ops.add(compiledWordResponse.op);
          command.opInfo.add(compiledWordResponse.opInfo);
          currentSentence = compiledWordResponse.remainingSentence.stripLeading();
          break;
        }
        if(word instanceof RecallWord) {
          break compileLoop;
        }
      }

    }
    return command;
  }


}
