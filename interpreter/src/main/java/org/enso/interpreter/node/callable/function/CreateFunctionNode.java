package org.enso.interpreter.node.callable.function;

import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.frame.VirtualFrame;
import org.enso.interpreter.node.EnsoRootNode;
import org.enso.interpreter.node.ExpressionNode;
import org.enso.interpreter.runtime.callable.argument.ArgumentDefinition;
import org.enso.interpreter.runtime.callable.function.Function;

public class CreateFunctionNode extends ExpressionNode {
  private final RootCallTarget callTarget;
  private final ArgumentDefinition[] args;

  public CreateFunctionNode(RootCallTarget callTarget, ArgumentDefinition[] args) {
    this.callTarget = callTarget;
    this.args = args;
  }

  @Override
  public void markTail() {
    ((EnsoRootNode) callTarget.getRootNode()).markTail();
  }

  @Override
  public Function executeFunction(VirtualFrame frame) {
    MaterializedFrame scope = frame.materialize();
    return new Function(callTarget, scope, this.args);
  }

  @Override
  public Object executeGeneric(VirtualFrame frame) {
    return executeFunction(frame);
  }
}
