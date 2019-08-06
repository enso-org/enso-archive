package org.enso.interpreter.runtime.callable;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import org.enso.interpreter.runtime.callable.argument.ArgumentDefinition;

public abstract class Callable {
  private @CompilationFinal(dimensions = 1) ArgumentDefinition[] args;

  public Callable(ArgumentDefinition[] args) {
    this.args = args;
  }

  public ArgumentDefinition[] getArgs() {
    return this.args;
  }
}
