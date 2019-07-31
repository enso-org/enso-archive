package org.enso.interpreter.runtime;

import java.util.List;
import org.enso.interpreter.node.function.argument.ArgumentDefinition;

public abstract class Callable {
  private final List<ArgumentDefinition> args;

  public Callable(List<ArgumentDefinition> args) {
    this.args = args;
  }

  public List<ArgumentDefinition> getArgs() {
    return this.args;
  }
}
