package org.enso.interpreter.node.controlflow;

import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.NodeInfo;
import org.enso.interpreter.node.ExpressionNode;
import org.enso.interpreter.node.StatementNode;
import org.enso.interpreter.runtime.FramePointer;
import scala.reflect.internal.Trees;

@NodeInfo(shortName="=", description = "Assigns variable to an expression.")
public final class AssignmentNode extends StatementNode {

  @Child private ExpressionNode expression;
  private FramePointer framePtr;

  public AssignmentNode(FramePointer framePtr, ExpressionNode expression) {
    this.framePtr = framePtr;
    this.expression = expression;
  }

  @Override
  public void execute(VirtualFrame frame) {
    Object result = expression.executeGeneric(frame);
    framePtr.store(frame, result);
  }
}
