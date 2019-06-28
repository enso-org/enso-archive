package org.enso.interpreter.node.controlflow;

import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.NodeInfo;
import org.enso.interpreter.node.ExpressionNode;
import org.enso.interpreter.node.StatementNode;
import scala.reflect.internal.Trees;

@NodeInfo(shortName="=", description = "Assigns variable to an expression.")
public final class AssignmentNode extends StatementNode {

  @Child private ExpressionNode expression;
  private FrameSlot frameSlot;

  public AssignmentNode(FrameSlot frameSlot, ExpressionNode expression) {
    this.frameSlot = frameSlot;
    this.expression = expression;
  }

  @Override
  public void execute(VirtualFrame frame) {
    Object result = expression.executeGeneric(frame);
    frame.setObject(frameSlot, result);
  }
}
