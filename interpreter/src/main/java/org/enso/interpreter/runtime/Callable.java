package org.enso.interpreter.runtime;

import java.util.List;
import org.enso.interpreter.node.function.argument.ArgumentDefinition;

public abstract class Callable {
  private final ArgumentDefinition[] args;

  public Callable(ArgumentDefinition[] args) {
    this.args = args;
  }

  public ArgumentDefinition[] getArgs() {
    return this.args;
  }
}
