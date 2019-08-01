package org.enso.interpreter.runtime.function;

import org.enso.interpreter.runtime.function.argument.ArgumentDefinition;

public abstract class Callable {
  private final ArgumentDefinition[] args;

  public Callable(ArgumentDefinition[] args) {
    this.args = args;
  }

  public ArgumentDefinition[] getArgs() {
    return this.args;
  }
}
