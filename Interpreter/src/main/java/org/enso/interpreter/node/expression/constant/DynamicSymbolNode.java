package org.enso.interpreter.node.expression.constant;

import com.oracle.truffle.api.frame.VirtualFrame;
import org.enso.interpreter.node.ExpressionNode;
import org.enso.interpreter.runtime.callable.DynamicSymbol;

/** Simple constant node that always results in the same {@link DynamicSymbol}. */
public class DynamicSymbolNode extends ExpressionNode {
  private final DynamicSymbol dynamicSymbol;

  /** @param dynamicSymbol the symbol to always be this node's value. */
  public DynamicSymbolNode(DynamicSymbol dynamicSymbol) {
    this.dynamicSymbol = dynamicSymbol;
  }

  /**
   * @param frame the stack frame for execution
   * @return the constant {@link DynamicSymbol}
   */
  @Override
  public Object executeGeneric(VirtualFrame frame) {
    return dynamicSymbol;
  }
}
