package org.enso.interpreter.node.function.argument;

import java.util.Optional;
import org.enso.interpreter.node.ExpressionNode;

public class ArgumentDefinitionNode {
  private final int position;
  private final String name;
  private final Optional<ExpressionNode> defaultValue;

  public ArgumentDefinitionNode(int position, String name) {
    this.position = position;
    this.name = name;
    this.defaultValue = Optional.empty();
  }

  public ArgumentDefinitionNode(int position, String name,
      ExpressionNode defaultValue) {
    this.position = position;
    this.name = name;
    this.defaultValue = Optional.of(defaultValue);
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
