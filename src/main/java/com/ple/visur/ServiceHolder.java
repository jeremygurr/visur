package com.ple.visur;

public class ServiceHolder {

  public static ServiceHolder init() {
    return new ServiceHolder();
  }

  ServiceHolder() {
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
