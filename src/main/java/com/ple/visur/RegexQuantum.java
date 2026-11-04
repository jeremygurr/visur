package com.ple.visur;

import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RegexQuantum extends Quantum {
  String name;
  Pattern pattern;
  EditorModelCoupler emc = ServiceHolder.editorModelCoupler;

  public RegexQuantum(String name, String regexSource) {
    this.name = name;
    pattern = Pattern.compile(regexSource);
  }

  public String getName() {
    return name;
  }

  /**
   * The quantum bounds for the live model: reads the content and cursor line, delegates the arithmetic to
   * {@link #boundsFor}, then snaps the cursor to the start of what was selected. Zeroing the span when the range
   * collapses used to happen here too; nothing zeroes it now, because only {@code s} writes the span.
   */
  @Override
  public int[] getBoundaries(int realCA, ArrayList<Integer> newlineIndices, int span, boolean includeTail) {
    int[] bounds = boundsFor(pattern, emc.getEditorContent(), newlineIndices, realCA, emc.getCY(), span);
    emc.putCA(bounds[0]); // legacy side effect: the cursor snaps to the start of the quantum it selected
    return bounds;
  }

  /**
   * The selection for a span of {@code span} quanta starting at {@code ca}, as {@code [start, end)}.
   *
   * <p>Each unit is one match of {@code pattern}, and the range is contiguous, so the text between two selected
   * words — the whitespace, or a line break — is inside the selection rather than skipped. A span that runs out of
   * quanta stops at the end of the last one rather than taking the trailing whitespace with it, and the requested span
   * is never reduced.
   *
   * <p>With no quantum under the cursor there is nothing to span, so the range is empty — the answer a cursor on an
   * indent or between two words gives is the answer it gives on a blank line. The span itself survives: only {@code s}
   * writes that.
   *
   * @param nl not read: a quantum's extent is set by its pattern, not by the line structure. Part of the signature so
   *           all four quanta's seam takes the same arguments.
   * @param cy not read, for the same reason
   */
  static int[] boundsFor(Pattern pattern, String editorContent, ArrayList<Integer> nl, int ca, int cy, int span) {
    if(span > 0 && !matchesAt(pattern, editorContent, ca)) {
      return new int[]{ca, ca};
    }
    int leftBound = ca;
    int rightBound = leftBound;
    if(span > 0) {
      leftBound = quantumStart(pattern, editorContent, leftBound);
    } else if(isInMiddleOfQuantum(pattern, editorContent, leftBound)) {
      leftBound = getPrevBound(pattern, editorContent, leftBound);
    }
    int spansRemaining = span;
    while(spansRemaining > 0) {
      rightBound = quantumEnd(pattern, editorContent, rightBound);
      spansRemaining--;
      if(spansRemaining > 0) {
        rightBound = getNextBound(pattern, editorContent, rightBound);
      }
    }
    return new int[]{leftBound, rightBound};
  }

  /** Whether {@code bound} sits inside a quantum rather than at one of its edges. */
  private static boolean isInMiddleOfQuantum(Pattern pattern, String editorContent, int bound) {
    if(bound > 0 && bound < editorContent.length()) {
      Matcher prevMatcher = pattern.matcher(editorContent.substring(bound - 1, bound));
      Matcher nextMatcher = pattern.matcher(editorContent.substring(bound, bound + 1));
      boolean matchExistsBefore = prevMatcher.matches();
      boolean matchExistsAfter = nextMatcher.matches();
      return matchExistsBefore && matchExistsAfter;
    } else {
      return false;
    }
  }

  /** Walks back to the first index that still matches, which is where the quantum under {@code start} begins. */
  private static int quantumStart(Pattern pattern, String editorContent, int start) {
    int leftBound = start;
    boolean endFound = false;
    while(!endFound) {
      if(leftBound > 0) {
        Matcher matcher = pattern.matcher(editorContent.substring(leftBound - 1, leftBound));
        if (matcher.matches()) {
          leftBound--;
        } else {
          endFound = true;
        }
      } else {
        endFound = true;
      }
    }
    return leftBound;
  }

  /** Walks forward to the first index past the quantum that starts at or contains {@code start}. */
  private static int quantumEnd(Pattern pattern, String editorContent, int start) {
    int rightBound = start;
    boolean endFound = false;
    while(!endFound) {
      if(rightBound < editorContent.length()) {
        Matcher matcher = pattern.matcher(editorContent.substring(rightBound, rightBound + 1));
        if (matcher.matches()) {
          rightBound++;
        } else {
          endFound = true;
        }
      } else {
        endFound = true;
      }
    }
    return rightBound;
  }

  /** iteratively loop through every dx and dy variable passed into the move method via mv.dx and/or mv.dy
   * make a start var whose value is ca before movement
   * if mv.dx != 0, call moveLeftRight method (all the same inputs + start var).
     * assign return value of moveLeftRight to caDestination var, which will be plugged into moveUpDown for start param
   * likewise, if mv.dy != 0, call moveUpDown method (this time using caDestination as its start parameter)
     * also assign return value of moveUpDown to caDestination
   * return the resulting caDestination var
   * @param editorContent source of content which cursor is moving on
   * @param newlineIndices indices where newline chars occur
   * @param mv vector where movement occurs on both the x and y axes
   * @return resulting ca coordinate from move method
   */
  @Override
  public int move(String editorContent, ArrayList<Integer> newlineIndices, MovementVector mv) {
    int startingCA = emc.getCA();
    int destination = startingCA;
    int span = emc.getSpan();
    int spansRemaining = span;
    int incrementer = mv.dx > 0 ? 1 : -1;
    while(mv.dx != 0) {
      destination = mv.dx > 0 ? getNextBound(pattern, editorContent, destination) : getPrevBound(pattern, editorContent, destination);
      if(span > 0 && isAtBeginningOfQuantum(destination)) {
        spansRemaining--;
      }
      while(spansRemaining > 0) {
        destination = mv.dx > 0 ? getNextQuantumStart(destination) : getPrevQuantumStart(destination);
        spansRemaining--;
        if(spansRemaining > 0) {
          destination = mv.dx > 0 ? getNextBound(pattern, editorContent, destination) : getPrevBound(pattern, editorContent, destination);
        }
      }
      emc.putCA(destination);
      emc.putVirtualCX(emc.getCX());
      mv.dx -= incrementer;
      boolean destinationIsInWrongPlace = mv.dx == 0 && span > 0 && !isAtBeginningOfQuantum(destination);
      if(destinationIsInWrongPlace) {
        destination = startingCA;
      }
    }
    while(mv.dy != 0) {
      CharacterQuantum cq = new CharacterQuantum();
      destination = cq.move(editorContent, newlineIndices, mv);
      mv.dy -= mv.dy > 0 ? 1 : -1;
    }
    return destination;
  }


  private boolean isAtBeginningOfQuantum(int bound) {
    String nextString = emc.getStrToMatch(true, bound);
    Matcher nextStringMatcher = pattern.matcher(nextString);
    boolean nextStringMatches = nextStringMatcher.matches();
    boolean prevStringMatches = false;
    if(bound > 0) {
      String prevString = emc.getStrToMatch(false, bound);
      Matcher prevStringMatcher = pattern.matcher(prevString);
      prevStringMatches = prevStringMatcher.matches();
    }
    return nextStringMatches && !prevStringMatches;
  }

  private int getNextQuantumStart(int bound) {
    String editorContent = emc.getEditorContent();
    int nextStart = bound;
    boolean matchFound = false;
    while(!matchFound) {
      if(nextStart >= editorContent.length() - 1) {
        break;
      }
      String strToMatch = editorContent.substring(nextStart, nextStart + 1);
      Matcher matcher = pattern.matcher(strToMatch);
      if(matcher.matches()) {
        matchFound = true;
      } else {
        nextStart++;
      }
    }
    if(!matchFound) {
      nextStart = bound;
    }
    return nextStart;
  }

  private int getPrevQuantumStart(int bound) {
    int prevStart = bound;
    boolean matchFound = false;
    while(!matchFound) {
      if(isAtBeginningOfQuantum(prevStart)) {
        matchFound = true;
      } else {
        prevStart--;
      }
    }
    if(!matchFound) {
      prevStart = bound;
    }
    return prevStart;
  }

  /**
   * The end of the quantum at {@code bound}, or — when {@code bound} is in the whitespace after a quantum — the start
   * of the next one.
   *
   * <p>It used to step one character when called on whitespace, which is invisible across a single space and wrong
   * across every wider gap: "three words" from {@code "one   two   three"} selected {@code "one  "}, one word and two
   * characters, because each remaining span bought one character of whitespace. It also capped how far a word range
   * could ever reach, since it grew one character per span across a blank run — see
   * <a href="../../../../../docs/wayfinder/span-and-count/issues/07-frame-at-span-one.md">ticket 07</a>.
   *
   * <p>Returns {@code bound} when nothing matches ahead: a walk that runs out of quanta stops rather than advancing
   * past the last one.
   */
  static int getNextBound(Pattern pattern, String editorContent, int bound) {
    if(!matchesAt(pattern, editorContent, bound)) {
      return startOfNextQuantum(pattern, editorContent, bound);
    }
    return quantumEnd(pattern, editorContent, bound);
  }

  private static int startOfNextQuantum(Pattern pattern, String editorContent, int bound) {
    int cursor = bound;
    while(cursor < editorContent.length() && !matchesAt(pattern, editorContent, cursor)) {
      cursor++;
    }
    return cursor < editorContent.length() ? cursor : bound;
  }

  /** Whether a quantum starts or continues at {@code bound}. */
  private static boolean matchesAt(Pattern pattern, String editorContent, int bound) {
    if(bound < 0 || bound >= editorContent.length()) {
      return false;
    }
    return pattern.matcher(editorContent.substring(bound, bound + 1)).matches();
  }

  /** it's the same idea as getNextBound, but it searches backwards
   * @return
   */
  static int getPrevBound(Pattern pattern, String editorContent, int bound) {
    boolean invalidBoundFound = bound == 0;
    boolean firstWasMatch = false;
    if(bound > 0) {
      String strToMatch = editorContent.substring(bound - 1, bound);
      Matcher matcher = pattern.matcher(strToMatch);
      firstWasMatch = matcher.matches();
      bound--;
    }
    if(bound > 0) {
      while (!invalidBoundFound && firstWasMatch) {
        Matcher matcher = pattern.matcher(editorContent.substring(bound - 1, bound));
        if (matcher.matches()) {
          bound--;
          if (bound == 0) {
            invalidBoundFound = true;
          }
        } else {
          invalidBoundFound = true;
        }
      }
    }
    return bound;
  }

}
