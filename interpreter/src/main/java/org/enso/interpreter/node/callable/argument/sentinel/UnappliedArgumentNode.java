package org.enso.interpreter.node.callable.argument.sentinel;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.NodeInfo;
import org.enso.interpreter.node.ExpressionNode;
import org.enso.interpreter.runtime.error.UnsaturatedCallException;
import org.enso.interpreter.runtime.callable.argument.ArgumentDefinition;

@NodeInfo(shortName = "_", description = "An unapplied argument")
public class UnappliedArgumentNode extends ExpressionNode {
  private ArgumentDefinition argument;

  public UnappliedArgumentNode(ArgumentDefinition argument) {
    this.argument = argument;
  }

  @Override
  public Object executeGeneric(VirtualFrame frame) {
    throw new UnsaturatedCallException(this.argument);
  }
}
