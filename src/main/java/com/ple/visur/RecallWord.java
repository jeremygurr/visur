package com.ple.visur;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RecallWord implements Word {

  @Override
  public CompiledWordResponse compile(String sentence) {
    Pattern pattern = Pattern.compile("([^\\s]+)(.*)");
    Matcher matcher = pattern.matcher(sentence);
    if(matcher.matches()) {
      CompiledWordResponse cwr;
      Operator op = new RecallOp();
      VariableMap gvm = ServiceHolder.editorModelCoupler.session().getGlobalVarMap();
      String recallWordName = matcher.group(1);
      if(gvm.get(recallWordName) != null) {
        cwr = new CompiledWordResponse(op, recallWordName, matcher.group(2));
      } else {
        cwr = null;
      }
      return cwr;
    } else {
      return null;
    }
  }

}
