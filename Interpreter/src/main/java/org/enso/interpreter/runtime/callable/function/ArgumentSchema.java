package org.enso.interpreter.runtime.callable.function;

import com.oracle.truffle.api.CompilerDirectives;
import org.enso.interpreter.runtime.callable.argument.ArgumentDefinition;

public class ArgumentSchema {
  private final @CompilerDirectives.CompilationFinal(dimensions = 1) ArgumentDefinition[]
      argumentInfos;
  private final @CompilerDirectives.CompilationFinal(dimensions = 1) boolean[] hasPreApplied;
  private final boolean hasAnyPreApplied;

  public ArgumentSchema(ArgumentDefinition[] argumentInfos, boolean[] hasPreApplied) {
    this.argumentInfos = argumentInfos;
    this.hasPreApplied = hasPreApplied;
    boolean hasAnyPreApplied = false;
    for (int i = 0; i < hasPreApplied.length; i++) {
      if (hasPreApplied[i]) {
        hasAnyPreApplied = true;
        break;
      }
    }
    this.hasAnyPreApplied = hasAnyPreApplied;
  }

  public ArgumentSchema(ArgumentDefinition[] argumentInfos) {
    this(argumentInfos, new boolean[argumentInfos.length]);
  }

  public boolean hasPreAppliedAt(int i) {
    return hasPreApplied[i];
  }

  public boolean hasDefaultAt(int i) {
    return argumentInfos[i].hasDefaultValue();
  }

  public ArgumentDefinition[] getArgumentInfos() {
    return argumentInfos;
  }

  public boolean[] cloneHasPreApplied() {
    return hasPreApplied.clone();
  }

  public boolean hasAnyPreApplied() {
    return hasAnyPreApplied;
  }

  public int getArgumentsCount() {
    return argumentInfos.length;
  }

}
