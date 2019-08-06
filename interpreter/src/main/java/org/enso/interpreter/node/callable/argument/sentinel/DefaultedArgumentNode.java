package org.enso.interpreter.node.callable.argument.sentinel;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.NodeInfo;
import org.enso.interpreter.node.ExpressionNode;
import org.enso.interpreter.runtime.callable.argument.ArgumentDefinition;

@NodeInfo(shortName = "_", description = "An unapplied argument")
public class DefaultedArgumentNode extends ExpressionNode {
  private ArgumentDefinition argument;

  public DefaultedArgumentNode(ArgumentDefinition argument) {
    this.argument = argument;
  }


  //TODO [AA]: Fix it. Or remove it.
  @Override
  public Object executeGeneric(VirtualFrame frame) {
    throw new RuntimeException("Fatal Error: Attempted to execute a defaulted argument.");
  }
}
