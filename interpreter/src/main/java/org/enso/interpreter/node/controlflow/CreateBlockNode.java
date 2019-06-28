package org.enso.interpreter.node.controlflow;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.RootNode;
import org.enso.interpreter.node.EnsoRootNode;
import org.enso.interpreter.node.ExpressionNode;
import org.enso.interpreter.runtime.Block;
import org.enso.interpreter.runtime.Context;


public class CreateBlockNode extends ExpressionNode {
  @Child private BlockNode expr;

  public CreateBlockNode(BlockNode expr) {
    this.expr = expr;
  }

  @Override
  public Object executeGeneric(VirtualFrame frame) {
    RootCallTarget callTarget = Truffle.getRuntime().createCallTarget(expr);
    MaterializedFrame scope = frame.materialize();
    return new Block(callTarget, scope);
  }
}
