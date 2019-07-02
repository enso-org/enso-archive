package org.enso.interpreter.node.function;

import com.oracle.truffle.api.*;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.RootNode;
import org.enso.interpreter.node.EnsoRootNode;
import org.enso.interpreter.node.ExpressionNode;
import org.enso.interpreter.runtime.Block;
import org.enso.interpreter.runtime.Context;


public class CreateFunctionNode extends ExpressionNode {
  private final RootCallTarget callTarget;

  public CreateFunctionNode(RootCallTarget callTarget) {
    this.callTarget = callTarget;
  }

  @Override
  public Object executeGeneric(VirtualFrame frame) {
    MaterializedFrame scope = frame.materialize();
    return new Block(callTarget, scope);
  }
}
