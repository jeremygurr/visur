package com.ple.visur;

/** Pure boundary adjustment for a quantum's following separator. */
final class Separators {
  private Separators() {}

  static int[] including(String content, int[] bounds, String separators) {
    if(bounds[1] <= bounds[0] || separators.isEmpty()) {
      return bounds;
    }
    int end = bounds[1];
    while(end < content.length() && separators.indexOf(content.charAt(end)) >= 0) {
      end++;
    }
    return new int[]{bounds[0], end};
  }
}
