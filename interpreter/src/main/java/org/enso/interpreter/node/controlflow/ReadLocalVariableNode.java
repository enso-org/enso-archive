package org.enso.interpreter.node.controlflow;

import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.NodeInfo;
import org.enso.interpreter.node.ExpressionNode;
import org.enso.interpreter.runtime.FramePointer;

@NodeInfo(shortName = "readVar", description = "Access local variable value.")
public final class ReadLocalVariableNode extends ExpressionNode {
  private FramePointer pointer;

  public ReadLocalVariableNode(FramePointer pointer) {
    this.pointer = pointer;
  }

  @Override
  public Object executeGeneric(VirtualFrame frame) {
    Frame currentFrame = frame;
    while (currentFrame != null) {
      if (pointer.getFrameDescriptor() == currentFrame.getFrameDescriptor())
        return frame.getValue(pointer.getFrameSlot());
      currentFrame = (Frame) currentFrame.getArguments()[0];
    }
    return null;
  }
}
