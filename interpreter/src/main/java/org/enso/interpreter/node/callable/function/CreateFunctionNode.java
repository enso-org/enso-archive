package org.enso.interpreter.node.callable.function;

import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.frame.VirtualFrame;
import org.enso.interpreter.node.EnsoRootNode;
import org.enso.interpreter.node.ExpressionNode;
import org.enso.interpreter.runtime.callable.argument.ArgumentDefinition;
import org.enso.interpreter.runtime.callable.function.Function;

/**
 * This node is responsible for representing the definition of a function. It contains information
 * about the function's arguments, as well as the target for calling said function.
 */
public class CreateFunctionNode extends ExpressionNode {
  private final RootCallTarget callTarget;
  private final ArgumentDefinition[] args;

  /**
   * Creates a new node to represent a function definition.
   *
   * @param callTarget the target for calling the function represented by this node
   * @param args information on the arguments to the function
   */
  public CreateFunctionNode(RootCallTarget callTarget, ArgumentDefinition[] args) {
    this.callTarget = callTarget;
    this.args = args;
  }

  /** Marks the function as being tail-recursive. */
  @Override
  public void markTail() {
    ((EnsoRootNode) callTarget.getRootNode()).markTail();
  }

  /** Marks the function as not being tail-recursive. */
  @Override
  public void markNotTail() {
    ((EnsoRootNode) callTarget.getRootNode()).markNotTail();
  }

  /**
   * Sets the tail-recursiveness of the function.
   *
   * @param isTail whether or not the function is tail-recursive
   */
  @Override
  public void setTail(boolean isTail) {
    ((EnsoRootNode) callTarget.getRootNode()).setTail(isTail);
  }

  /**
   * Generates the provided function definition in the given stack {@code frame}.
   *
   * @param frame the stack frame for execution
   * @return the function defined by this node
   */
  @Override
  public Function executeFunction(VirtualFrame frame) {
    MaterializedFrame scope = frame.materialize();
    return new Function(callTarget, scope, this.args);
  }

  /**
   * Executes the current node.
   *
   * @param frame the stack frame for execution
   * @return the result of executing the node
   */
  @Override
  public Object executeGeneric(VirtualFrame frame) {
    return executeFunction(frame);
  }
}
