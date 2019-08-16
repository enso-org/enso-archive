package org.enso.interpreter.node.expression.atom;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import org.enso.interpreter.node.ExpressionNode;
import org.enso.interpreter.runtime.callable.atom.AtomConstructor;

public class InstantiateNode extends ExpressionNode {
  private final AtomConstructor constructor;
  private @Children ExpressionNode[] arguments;

  public InstantiateNode(AtomConstructor constructor, ExpressionNode[] arguments) {
    this.constructor = constructor;
    this.arguments = arguments;
  }

  @Override
  @ExplodeLoop
  public Object executeGeneric(VirtualFrame frame) {
    Object[] argumentValues = new Object[arguments.length];
    for (int i = 0; i < arguments.length; i++) {
      argumentValues[i] = arguments[i].executeGeneric(frame);
    }
    return constructor.newInstance(argumentValues);
  }
}
