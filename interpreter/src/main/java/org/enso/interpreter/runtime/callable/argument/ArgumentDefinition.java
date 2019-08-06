package org.enso.interpreter.runtime.callable.argument;

import java.util.Optional;
import org.enso.interpreter.node.ExpressionNode;

public class ArgumentDefinition {
  private final int position;
  private final String name;
  // TODO[MK, AA]: Remove this field.
  private final Optional<ExpressionNode> defaultValue;

  public ArgumentDefinition(int position, String name) {
    this(position, name, null);
  }

  public ArgumentDefinition(int position, String name,
      ExpressionNode defaultValue) {
    this.position = position;
    this.name = name;
    this.defaultValue = Optional.ofNullable(defaultValue);
  }

  public int getPosition() {
    return this.position;
  }

  public String getName() {
    return this.name;
  }

  public Optional<ExpressionNode> getDefaultValue() {
    return this.defaultValue;
  }

  public boolean hasDefaultValue() {
    return this.defaultValue.isPresent();
  }
}
