package com.ple.visur;

public class ServiceHolder {

  public static ServiceHolder init() {
    return new ServiceHolder();
  }

  ServiceHolder() {
    // A new model means no windows on it, and the sessions live in a static map because a socket id is the only name
    // they have. Without this the session outlives the model it was reading: the next test in the JVM inherits the last
    // one's cursor, its command line and — the case that proved it — a question it was never asked and cannot unhear.
    Connections.closeAll();
    Clipboard.reset();
    editorModelCoupler = EditorModelCoupler.make();
    commandCompileService = CommandCompileService.make();
    initializerService = InitializerService.make(editorModelCoupler);
    commandExecutionService = CommandExecutionService.make();
    commandStateService = CommandStateService.make();
    modeSwitchService = ModeSwitchService.make();
  }

  public static EditorModelCoupler editorModelCoupler;
  public static CommandCompileService commandCompileService;
  public static CommandExecutionService commandExecutionService;
  public static CommandStateService commandStateService;
  public static ModeSwitchService modeSwitchService;
  public static InitializerService initializerService;
  //OperatorService is an interface, so I'm guessing it doesn't belong here, but all classes that use it will
}
