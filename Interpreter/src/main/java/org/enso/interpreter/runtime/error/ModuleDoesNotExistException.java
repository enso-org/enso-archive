package org.enso.interpreter.runtime.error;

public class ModuleDoesNotExistException extends RuntimeException {
  public ModuleDoesNotExistException(String name) {
    super("Module " + name + " does not exist.");
  }
}
