package org.enso.interpreter.node.function.argument;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.NodeInfo;
import org.enso.interpreter.node.ExpressionNode;
import org.enso.interpreter.runtime.errors.UnsaturatedCallException;

@NodeInfo(shortName = "_", description = "An unapplied argument")
public class UnappliedArgument extends ExpressionNode {
  private ArgumentDefinition argument;

  public UnappliedArgument(ArgumentDefinition argument) {
    this.argument = argument;
  }

  @Override
  public Object executeGeneric(VirtualFrame frame) {
    throw new UnsaturatedCallException(this.argument);
  }
}
