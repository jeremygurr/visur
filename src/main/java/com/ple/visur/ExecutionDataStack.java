package com.ple.visur;

import io.vertx.core.shareddata.Shareable;

import java.util.Stack;

public class ExecutionDataStack implements Shareable {
  Stack<Object> stack = new Stack<>();
  public void clear() {
    stack.clear();
  }

  public Object peek() {
    return stack.peek();
  }
  public Object pop() {
    return stack.pop();
  }
  public void push(Object item) {
    stack.push(item);
  }
  public int size() {
    return stack.size();
  }

}
