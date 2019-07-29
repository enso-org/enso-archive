package org.enso.interpreter.node.function.argument;

import com.oracle.truffle.api.frame.VirtualFrame;
import java.util.Optional;
import org.enso.interpreter.node.ExpressionNode;
import org.enso.interpreter.runtime.AtomConstructor;

public class CallArgument extends ExpressionNode {
  private final Optional<String> name;
  private final Optional<ExpressionNode> expression;
  private final boolean isIgnore;
  private final int position;

  public CallArgument(ExpressionNode expression, int position) {
    this(null, expression, position);
  }

  public CallArgument(String name, int position) {
    this(name, null, position);
  }

  public CallArgument(String name, ExpressionNode expression, int position) {
    this.name = Optional.ofNullable(name);
    this.expression = Optional.ofNullable(expression);
    this.position = position;

    if (this.name.isPresent() && !this.expression.isPresent()) {
      this.isIgnore = true;
    } else {
      this.isIgnore = false;
    }
  }

  @Override
  public Object executeGeneric(VirtualFrame frame) {
    if (!isIgnore()) {
      return this.expression.get().executeGeneric(frame);
    } else {
      return AtomConstructor.UNIT.newInstance();
    }
  }

  public Optional<String> getName() {
    return this.name;
  }

  public Optional<ExpressionNode> getExpression() {
    return this.expression;
  }

  public boolean isIgnore() {
    return this.isIgnore;
  }

  public boolean isPositional() {
    return !this.name.isPresent();
  }

  public boolean isNamed() {
    return this.name.isPresent();
  }
}
