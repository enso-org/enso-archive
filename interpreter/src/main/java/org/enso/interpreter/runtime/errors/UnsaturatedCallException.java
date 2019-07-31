package org.enso.interpreter.runtime.errors;

import org.enso.interpreter.node.function.argument.ArgumentDefinition;

public class UnsaturatedCallException extends RuntimeException {
  public UnsaturatedCallException(ArgumentDefinition arg) {
    super("The argument named " + arg.getName() + " has not been applied.");
  }
}
