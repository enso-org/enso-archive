package org.enso.interpreter.node.controlflow;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.NodeChildren;
import com.oracle.truffle.api.dsl.NodeField;
import com.oracle.truffle.api.dsl.NodeFields;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.NodeInfo;
import org.enso.interpreter.node.ExpressionNode;
import org.enso.interpreter.runtime.FramePointer;

@NodeInfo(shortName = "readVar", description = "Access local variable value.")
public abstract class ReadLocalVariableNode extends ExpressionNode {
  private final FrameSlot slot;
  private final int parentLevel;

  public ReadLocalVariableNode(FramePointer pointer) {
    this.slot = pointer.getFrameSlot();
    this.parentLevel = pointer.getParentLevel();
  }

  public MaterializedFrame getParentFrame(Frame frame) {
    return (MaterializedFrame) frame.getArguments()[0];
  }

  @ExplodeLoop
  public MaterializedFrame getProperFrame(Frame frame) {
    MaterializedFrame currentFrame = getParentFrame(frame);
    for (int i = 1; i < parentLevel; i++) {
      currentFrame = getParentFrame(currentFrame);
    }
    return currentFrame;
  }

  @Specialization(rewriteOn = FrameSlotTypeException.class)
  protected long readLong(VirtualFrame frame) throws FrameSlotTypeException {
    if (parentLevel == 0) return frame.getLong(slot);
    MaterializedFrame currentFrame = getProperFrame(frame);
    return currentFrame.getLong(slot);
  }

  @Specialization
  protected Object readGeneric(VirtualFrame frame) {
    if (parentLevel == 0) return FrameUtil.getObjectSafe(frame, slot);
    MaterializedFrame currentFrame = getProperFrame(frame);
    return FrameUtil.getObjectSafe(currentFrame, slot);
  }
}
