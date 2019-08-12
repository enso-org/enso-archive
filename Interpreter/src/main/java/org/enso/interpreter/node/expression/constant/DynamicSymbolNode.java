package org.enso.interpreter.node.expression.constant;

import com.oracle.truffle.api.frame.VirtualFrame;
import org.enso.interpreter.node.ExpressionNode;
import org.enso.interpreter.runtime.callable.DynamicSymbol;

public class DynamicSymbolNode extends ExpressionNode {
  private final DynamicSymbol dynamicSymbol;

  public DynamicSymbolNode(DynamicSymbol dynamicSymbol) {
    this.dynamicSymbol = dynamicSymbol;
  }

  @Override
  public Object executeGeneric(VirtualFrame frame) {
    return dynamicSymbol;
  }
}
