package org.enso.interpreter.node.callable.argument.sentinel;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.NodeInfo;
import org.enso.interpreter.node.ExpressionNode;
import org.enso.interpreter.runtime.callable.argument.ArgumentDefinition;

/** A representation of an argument that has been explicitly defaulted in the program source. */
@NodeInfo(shortName = "_", description = "An unapplied argument")
public class DefaultedArgumentNode extends ExpressionNode {
  private ArgumentDefinition argument;

  /**
   * Creates a defaulted argument sentinel value.
   *
   * @param argument information about the defaulted argument
   */
  public DefaultedArgumentNode(ArgumentDefinition argument) {
    this.argument = argument;
  }

  /**
   * Should not be called.
   *
   * <p>It is an error condition to try and execute a {@link DefaultedArgumentNode}, so this method
   * throws a {@link RuntimeException} if it happens.
   *
   * @param frame the stack frame to execute in
   * @return error
   */
  @Override
  public Object executeGeneric(VirtualFrame frame) {
    throw new RuntimeException(
        "Fatal Error: Attempted to execute a defaulted argument:" + this.argument);
  }
}
