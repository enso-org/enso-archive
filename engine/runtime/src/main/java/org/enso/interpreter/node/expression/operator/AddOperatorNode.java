package org.enso.interpreter.node.expression.operator;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.NodeInfo;
import org.enso.interpreter.node.ExpressionNode;
import org.enso.interpreter.node.callable.thunk.ForceNode;
import org.enso.interpreter.runtime.callable.argument.CallArgument;

/** The addition operator for Enso. */
@NodeInfo(shortName = "+")
public class AddOperatorNode extends BinaryOperatorNode {
  @Child private ExpressionNode left;
  @Child private ExpressionNode right;

  AddOperatorNode(ExpressionNode left, ExpressionNode right) {
    this.left = left;
    this.right = right;
  }

  /**
   * Creates an instance of this node.
   *
   * @param left the left operand
   * @param right the right operand
   * @return a node that adds {@code left} to {@code right}
   */
  public static AddOperatorNode build(CallArgument left, CallArgument right) {
    return new AddOperatorNode(
        ForceNode.build(left.getExpression()), ForceNode.build(right.getExpression()));
  }

  /**
   * Executes the node on long arguments.
   *
   * @param frame the stack frame for execution
   * @return the result of adding {@code left} and {@code right}
   */
  @Override
  public long executeLong(VirtualFrame frame) {
    return (long) left.executeGeneric(frame) + (long) right.executeGeneric(frame);
  }

  /**
   * Executes the node on generic arguments.
   *
   * @param frame the stack frame for execution
   * @return the result of adding {@code left} and {@code right}
   */
  @Override
  public Object executeGeneric(VirtualFrame frame) {
    return executeLong(frame);
  }
}
