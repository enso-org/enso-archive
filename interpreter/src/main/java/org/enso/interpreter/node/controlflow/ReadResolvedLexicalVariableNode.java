package org.enso.interpreter.node.controlflow;

import com.oracle.truffle.api.dsl.NodeField;
import com.oracle.truffle.api.dsl.NodeFields;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.FrameSlotTypeException;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.NodeInfo;
import org.enso.interpreter.node.ExpressionNode;

@NodeFields({
  @NodeField(name = "slot", type = FrameSlot.class),
  @NodeField(name = "scope", type = MaterializedFrame.class)
})
@NodeInfo(description = "direct lookup to lexical frame")
public abstract class ReadResolvedLexicalVariableNode extends ExpressionNode {
  public abstract FrameSlot getSlot();

  public abstract MaterializedFrame getScope();

  @Specialization(rewriteOn = FrameSlotTypeException.class)
  protected long readLong(VirtualFrame virtualFrame) throws FrameSlotTypeException {
    return getScope().getLong(getSlot());
  }

  @Specialization(rewriteOn = FrameSlotTypeException.class)
  protected Object readObject(VirtualFrame virtualFrame) throws FrameSlotTypeException {
    return getScope().getObject(getSlot());
  }

  @Specialization
  public Object read(VirtualFrame virtualFrame) {
    return getScope().getValue(getSlot());
  }
}
