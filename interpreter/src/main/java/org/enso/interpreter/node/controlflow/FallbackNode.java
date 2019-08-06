package org.enso.interpreter.node.controlflow;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.UnexpectedResultException;
import org.enso.interpreter.node.ExpressionNode;
import org.enso.interpreter.node.callable.ExecuteCallNode;
import org.enso.interpreter.node.callable.ExecuteCallNodeGen;
import org.enso.interpreter.runtime.callable.atom.Atom;
import org.enso.interpreter.runtime.callable.function.Function;

public class FallbackNode extends CaseNode {
  @Child private ExpressionNode functionNode;
  @Child private ExecuteCallNode executeCallNode = ExecuteCallNodeGen.create();

  public FallbackNode(ExpressionNode functionNode) {
    this.functionNode = functionNode;
  }

  @Override
  public void execute(VirtualFrame frame, Atom target) throws UnexpectedResultException {
    Function function = functionNode.executeFunction(frame);
    throw new BranchSelectedException(executeCallNode.executeCall(function, new Object[0]));
  }
}
