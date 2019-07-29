package org.enso.interpreter.runtime;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.enso.interpreter.node.function.argument.ArgumentDefinition;

public abstract class Callable {
  private final List<ArgumentDefinition> args;
  private final Map<String, ArgumentDefinition> fnArgsByName;
  private final Map<Integer, ArgumentDefinition> fnArgsByPosition;

  public Callable(List<ArgumentDefinition> args) {
    this.args = args;

    fnArgsByName =
        this.args.stream().collect(Collectors.toMap(ArgumentDefinition::getName, a -> a));
    fnArgsByPosition =
        this.args.stream().collect(Collectors.toMap(ArgumentDefinition::getPosition, a -> a));
  }

  public List<ArgumentDefinition> getArgs() {
    return this.args;
  }

  public Map<String, ArgumentDefinition> getFnArgsByName() {
    return fnArgsByName;
  }

  public Map<Integer, ArgumentDefinition> getFnArgsByPosition() {
    return fnArgsByPosition;
  }
}
