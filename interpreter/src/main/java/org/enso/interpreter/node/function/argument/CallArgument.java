package org.enso.interpreter.node.function.argument;

import com.oracle.truffle.api.frame.VirtualFrame;
import java.util.Optional;
import org.enso.interpreter.node.ExpressionNode;
import org.enso.interpreter.runtime.AtomConstructor;

public class CallArgument extends ExpressionNode {
  private final String name;
  @Child private ExpressionNode expression;

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

  @Override
  public Object executeGeneric(VirtualFrame frame) {
    if (!isIgnored()) {
      return this.expression.executeGeneric(frame); // Note [Execution Safety]
    } else {
      return AtomConstructor.UNIT.newInstance();
    }
  }

  /* Note [Execution Safety]
   * ~~~~~~~~~~~~~~~~~~~~~~~
   * It is safe to call `get` here as the only circumstance under which a call argument does not
   * contain an expression is when it is ignored.
   */

  public String getName() {
    return this.name;
  }
}
