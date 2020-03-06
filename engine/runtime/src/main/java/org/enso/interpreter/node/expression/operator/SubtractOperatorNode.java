package org.enso.interpreter.node.expression.operator;

import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.NodeInfo;
import org.enso.interpreter.node.ExpressionNode;
import org.enso.interpreter.node.callable.thunk.ForceNode;
import org.enso.interpreter.runtime.callable.argument.CallArgument;

/** The subtraction operator for Enso. */
@NodeInfo(shortName = "-")
public class SubtractOperatorNode extends BinaryOperatorNode {
  @Child private ExpressionNode left;
  @Child private ExpressionNode right;

  SubtractOperatorNode(ExpressionNode left, ExpressionNode right) {
    this.left = left;
    this.right = right;
  }

  /**
   * Creates an instance of this node.
   *
   * @param left the left operand
   * @param right the right operand
   * @return a node that subtracts {@code right} from {@code left}
   */
  public static SubtractOperatorNode build(CallArgument left, CallArgument right) {
    return new SubtractOperatorNode(
        ForceNode.build(left.getExpression()), ForceNode.build(right.getExpression()));
  }

  /**
   * Executes the node on long arguments.
   *
   * @param frame the stack frame for execution
   * @return the result of subtracting {@code right} from {@code left}
   */
  @Override
  public long executeLong(VirtualFrame frame) {
    return (long) left.executeGeneric(frame) - (long) right.executeGeneric(frame);
  }

  /**
   * Executes the node on generic arguments.
   *
   * @param frame the stack frame for execution
   * @return the result of subtracting {@code right} from {@code left}
   */
  @Override
  public Object executeGeneric(VirtualFrame frame) {
    return executeLong(frame);
  }
}
