package org.enso.interpreter.node;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import org.enso.interpreter.runtime.state.Stateful;

public class RunStateNode extends ExpressionNode {
  private @Node.Child ExpressionNode expr;

  public RunStateNode(ExpressionNode expr) {
    this.expr = expr;
  }

  @Override
  public Object executeGeneric(VirtualFrame frame) {
    Object result = expr.executeGeneric(frame);
    if (result instanceof Stateful) {
      return ((Stateful) result).getValue();
    }
    return result;
  }
}
