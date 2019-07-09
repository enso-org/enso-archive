package org.enso.interpreter.util;

import com.oracle.truffle.api.frame.VirtualFrame;
import java.util.List;
import org.enso.interpreter.AstAssignment;
import org.enso.interpreter.AstExpression;
import org.enso.interpreter.node.ExpressionNode;

public class GlobalScope extends ExpressionNode {
  private final List<AstAssignment> assignments;
  private final AstExpression expression;

  public GlobalScope(List<AstAssignment> assignments, AstExpression expression) {
    this.assignments = assignments;
    this.expression = expression;
  }

  @Override
  public Object executeGeneric(VirtualFrame frame) {
    return null;
  }
}
