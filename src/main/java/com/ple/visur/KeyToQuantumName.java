package com.ple.visur;

import io.vertx.core.shareddata.Shareable;

import java.util.HashMap;

public class KeyToQuantumName implements Shareable {
  HashMap<String, String> map = new HashMap<>();

  public String get(String key) {
    return map.get(key);
  }

  public java.util.Set<String> getKeys() {
    return map.keySet();
  }

  public void put(String key, String quantumName) {
    map.put(key, quantumName);
  }
}
