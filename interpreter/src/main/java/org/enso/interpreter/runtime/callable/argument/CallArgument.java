package org.enso.interpreter.runtime.callable.argument;

import org.enso.interpreter.node.ExpressionNode;

public class CallArgument {
  private final String name;
  private ExpressionNode expression;

  public CallArgument(ExpressionNode expression) {
    this(null, expression);
  }

  public CallArgument(String name) {
    this(name, null);
  }

  public CallArgument(String name, ExpressionNode expression) {
    this.name = name;
    this.expression = expression;
  }

  public boolean isIgnored() {
    return (this.name != null) && (this.expression == null);
  }

  public boolean isNamed() {
    return (this.name != null) && (this.expression != null);
  }

  public boolean isPositional() {
    return !isNamed() && !isIgnored();
  }

  /* Note [Execution Safety]
   * ~~~~~~~~~~~~~~~~~~~~~~~
   * It is safe to call `get` here as the only circumstance under which a call argument does not
   * contain an expression is when it is ignored.
   */

  public String getName() {
    return this.name;
  }

  public ExpressionNode getExpression() {
    return expression;
  }
}
