package org.enso.interpreter.node.controlflow;

import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.NodeInfo;
import org.enso.interpreter.node.ExpressionNode;
import org.enso.interpreter.runtime.FramePointer;

@NodeInfo(shortName = "readVar", description = "Access local variable value.")
public final class ReadLocalVariableNode extends ExpressionNode {
  private final FrameSlot slot;
  private final int parentLevel;

  public ReadLocalVariableNode(FramePointer pointer) {
    this.slot = pointer.getFrameSlot();
    this.parentLevel = pointer.getParentLevel();
  }

  @Override
  @ExplodeLoop
  public Object executeGeneric(VirtualFrame frame) {
    Frame currentFrame = frame;
    for (int i = 0; i < parentLevel; i++) {
      currentFrame = (Frame) currentFrame.getArguments()[0];
    }
    return currentFrame.getValue(slot);
  }
}
