package org.enso.interpreter.node.controlflow;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.NodeInfo;
import org.enso.interpreter.node.ExpressionNode;


@NodeInfo(description = "Read function argument.")
public class ReadArgumentNode extends ExpressionNode {
  private final int index;

  public ReadArgumentNode(int index) {
    this.index = index;
  }

  @Override
  public Object executeGeneric(VirtualFrame frame) {
    return ((Object[]) frame.getArguments()[1])[index];
  }
}
