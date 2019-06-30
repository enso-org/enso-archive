package org.enso.interpreter.node.controlflow;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.NodeChildren;
import com.oracle.truffle.api.dsl.NodeField;
import com.oracle.truffle.api.dsl.NodeFields;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.NodeInfo;
import org.enso.interpreter.node.ExpressionNode;
import org.enso.interpreter.runtime.FramePointer;

@NodeInfo(shortName = "readVar", description = "Access local variable value.")
//@NodeFields({@NodeField(name = "slot", type=FrameSlot.class), @NodeField(name="scope", type= MaterializedFrame.class)})
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
    if (parentLevel == 0) return frame.getValue(slot);
    MaterializedFrame currentFrame = (MaterializedFrame) frame.getArguments()[0];
    for (int i = 1; i < parentLevel; i++) {
      currentFrame = (MaterializedFrame) currentFrame.getArguments()[0];
    }
    CompilerDirectives.transferToInterpreterAndInvalidate();
    this.replace(ReadResolvedLexicalVariableNodeGen.create(slot, currentFrame));
    return currentFrame.getValue(slot);
  }


}
