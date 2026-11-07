package com.ple.visur;

public enum BusEvent {
  //sent by a client when its window changes size; consumed by ViewportWasChangedVerticle
  viewportWasChanged,

  //sent by a client for every key; consumed by KeyWasPressedVerticle
  keyWasPressed,

  //sent by a client that received a bracketed paste; consumed by KeyWasPressedVerticle
  pasteWasReceived,

  //sent by anything that changed the model; consumed by ModelWasChangedVerticle
  modelWasChanged,

  //sent by ModelWasChangedVerticle once per connection, and relayed to that connection's client by its socket
  viewWasChanged,

  //sent by a command the window asked to leave with (:q, :q!), and answered by that connection's socket closing it
  connectionShouldClose
}

