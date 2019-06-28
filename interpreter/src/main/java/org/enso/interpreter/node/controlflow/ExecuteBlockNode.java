package org.enso.interpreter.node.controlflow;

import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import com.oracle.truffle.api.nodes.NodeInfo;
import org.enso.interpreter.node.ExpressionNode;
import org.enso.interpreter.runtime.Block;

@NodeInfo(shortName = "@", description = "Executes block from children expression")
public final class ExecuteBlockNode extends ExpressionNode {
  @Child private ExpressionNode expression;
  @Child private IndirectCallNode callNode;

  public ExecuteBlockNode(ExpressionNode expression) {
    this.expression = expression;
    callNode = Truffle.getRuntime().createIndirectCallNode();
  }

  @Override
  public Object executeGeneric(VirtualFrame frame) {
    Block block = (Block) expression.executeGeneric(frame);
    Object[] args = {block.getScope()};
    return callNode.call(block.getCallTarget(), args);
  }
}
