package org.enso.interpreter.node.callable.argument.sentinel;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.NodeInfo;
import org.enso.interpreter.node.ExpressionNode;
import org.enso.interpreter.runtime.callable.argument.ArgumentDefinition;
import org.enso.interpreter.runtime.error.UnsaturatedCallException;

/**
 * A value used to sentinel an argument that has been defined for a function but not yet applied.
 */
@NodeInfo(shortName = "_", description = "An unapplied argument")
public class UnappliedArgumentNode extends ExpressionNode {
  private ArgumentDefinition argument;

  /**
   * Creates a sentinel value for the given argument.
   *
   * @param argument information about the argument being signalled
   */
  public UnappliedArgumentNode(ArgumentDefinition argument) {
    this.argument = argument;
  }

  /**
   * Should not be called.
   *
   * <p>It is an error condition to try and execute an {@link UnappliedArgumentNode}, so this method
   * throws a {@link UnsaturatedCallException} if it happens.
   *
   * @param frame the stack frame to execute in
   * @return error
   */
  @Override
  public Object executeGeneric(VirtualFrame frame) {
    throw new UnsaturatedCallException(this.argument);
  }
}
