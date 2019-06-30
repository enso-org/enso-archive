package org.enso.interpreter.node.controlflow;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import com.oracle.truffle.api.nodes.NodeInfo;
import org.enso.interpreter.TailCallException;
import org.enso.interpreter.node.ExpressionNode;
import org.enso.interpreter.runtime.Block;
import scala.reflect.api.Exprs;

@NodeInfo(shortName = "@", description = "Executes block from children expression")
public final class ExecuteBlockNode extends ExpressionNode {
  @Child private ExpressionNode expression;
  @Children private final ExpressionNode[] arguments;
  @Child private IndirectCallNode callNode;

  public ExecuteBlockNode(ExpressionNode expression, ExpressionNode[] arguments) {
    this.expression = expression;
    this.arguments = arguments;
    callNode = Truffle.getRuntime().createIndirectCallNode();
  }

  @Override
  @ExplodeLoop
  public Object executeGeneric(VirtualFrame frame) {
    Block block = (Block) expression.executeGeneric(frame);
    Object[] positionalArguments = new Object[arguments.length];
//    positionalArguments[0] = block.getScope();
    for (int i = 0; i < arguments.length; i++) {
      positionalArguments[i] = arguments[i].executeGeneric(frame);
    }
    Object[] args = {block.getScope(), positionalArguments};

    CompilerAsserts.compilationConstant(this.isTail());
    if (this.isTail()) {
      throw new TailCallException(block.getCallTarget(), args);
    } else {
      return doCall(block.getCallTarget(), args);
    }
  }

  private Object doCall(CallTarget target, Object[] args) {
    while (true) {
      try {
        return callNode.call(target, args);
      } catch (TailCallException e) {
        target = e.getCallTarget();
        args = e.getArguments();
      }
    }
  }
}
