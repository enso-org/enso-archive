package org.enso.interpreter.node.controlflow;

import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.NodeField;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.FrameSlotKind;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.NodeInfo;
import org.enso.interpreter.node.ExpressionNode;
import org.enso.interpreter.node.StatementNode;
import org.enso.interpreter.runtime.FramePointer;
import scala.reflect.api.Exprs;
import scala.reflect.internal.Trees;

@NodeInfo(shortName="=", description = "Assigns variable to an expression.")
@NodeChild(value = "rhsNode", type = ExpressionNode.class)
@NodeField(name="frameSlot", type=FrameSlot.class)
public abstract class AssignmentNode extends StatementNode {

  public abstract FrameSlot getFrameSlot();

  @Specialization
  protected void writeLong(VirtualFrame frame, long value) {
    frame.getFrameDescriptor().setFrameSlotKind(getFrameSlot(), FrameSlotKind.Long);
    frame.setLong(getFrameSlot(), value);
  }

  @Specialization
  protected void writeObject(VirtualFrame frame, Object value) {
    frame.getFrameDescriptor().setFrameSlotKind(getFrameSlot(), FrameSlotKind.Object);
    frame.setObject(getFrameSlot(), value);
  }

}
