package org.enso.interpreter.node.expression.literal;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import org.enso.interpreter.node.ExpressionNode;
import org.enso.interpreter.runtime.data.Vector;

public class VectorLiteralNode extends ExpressionNode {
  private @Children ExpressionNode[] items;

  private VectorLiteralNode(ExpressionNode[] items) {
    this.items = items;
  }

  public static VectorLiteralNode build(ExpressionNode[] items) {
    return new VectorLiteralNode(items);
  }

  @Override
  @ExplodeLoop
  public Object executeGeneric(VirtualFrame frame) {
    Object[] itemValues = new Object[items.length];
    for (int i = 0; i < items.length; i++) {
      itemValues[i] = items[i].executeGeneric(frame);
    }
    return new Vector(itemValues);
  }
}
