package com.ple.visur;

public enum BusEvent {
  //sent by a client when its window changes size; consumed by ViewportWasChangedVerticle
  viewportWasChanged,

  //sent by a client for every key; consumed by KeyWasPressedVerticle
  keyWasPressed,

  //sent by a client that received a bracketed paste; consumed by KeyWasPressedVerticle
  pasteWasReceived,

  //sent by a client immediately before it closes its own socket, to say that this window is leaving rather than being
  //lost. Answered by the socket that received it rather than by a verticle, because the answer is read by that socket's
  //close handler and a bus message can arrive after it; see MainVerticle.
  windowWasClosed,

  //sent by anything that changed the model; consumed by ModelWasChangedVerticle
  modelWasChanged,

  //sent by ModelWasChangedVerticle once per connection, and relayed to that connection's client by its socket
  viewWasChanged,

  //sent by a command the window asked to leave with (:q, :q!), and answered by that connection's socket closing it
  connectionShouldClose,

  //sent once per command that copied something, to the connection whose keys copied it, asking that client to write
  //the text it carries into the clipboard of the machine visur is being typed into
  clipboardWriteRequested,

  //sent to one connection when the server needs to know what its clipboard holds and its own memory of the last write
  //will not answer for it; answered by the frame below
  clipboardReadRequested,

  //sent by a client answering clipboardReadRequested: `{"text": …}` when the read worked — `{"text": ""}` is an empty
  //clipboard, which is a reply and not a failure — and with no text field at all when it did not
  clipboardContents
}

