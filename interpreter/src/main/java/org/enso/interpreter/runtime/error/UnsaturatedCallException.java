package org.enso.interpreter.runtime.error;

import org.enso.interpreter.runtime.function.argument.ArgumentDefinition;

public class UnsaturatedCallException extends RuntimeException {
  public UnsaturatedCallException(ArgumentDefinition arg) {
    super("The argument named " + arg.getName() + " has not been applied.");
  }
}
