package com.ple.visur;

public class StringVisurVar implements VisurVar {
  String val;

  StringVisurVar(String val) {
    this.val = val;
  }

  @Override
  public String getString() {
    return null;
  }

  @Override
  public int getInt() {
    return 0;
  }

  @Override
  public boolean getBoolean() {
    return false;
  }

  @Override
  public void put(String v) {

  }

  @Override
  public void put(int v) {

  }

  @Override
  public void put(boolean v) {

  }
}