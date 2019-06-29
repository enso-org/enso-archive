package org.enso.interpreter.node.controlflow;

import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import com.oracle.truffle.api.nodes.NodeInfo;
import org.enso.interpreter.node.ExpressionNode;
import org.enso.interpreter.runtime.Block;
import scala.reflect.api.Exprs;

@NodeInfo(shortName = "@", description = "Executes block from children expression")
public final class ExecuteBlockNode extends ExpressionNode {
  @Child private ExpressionNode expression;
  @Children private ExpressionNode[] arguments;
  @Child private IndirectCallNode callNode;

  public ExecuteBlockNode(ExpressionNode expression, ExpressionNode[] arguments) {
    this.expression = expression;
    this.arguments = arguments;
    callNode = Truffle.getRuntime().createIndirectCallNode();
  }

  @Override
  public Object executeGeneric(VirtualFrame frame) {
    Block block = (Block) expression.executeGeneric(frame);
    Object[] positionalArguments = new Object[arguments.length + 1];
    positionalArguments[0] = block.getScope();
    for (int i = 0; i < arguments.length; i++) {
      positionalArguments[i + 1] = arguments[i].executeGeneric(frame);
    }
    return callNode.call(block.getCallTarget(), positionalArguments);
  }
}
