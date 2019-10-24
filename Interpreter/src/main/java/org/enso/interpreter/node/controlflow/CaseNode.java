package org.enso.interpreter.node.controlflow;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.UnexpectedResultException;
import org.enso.interpreter.node.BaseNode;
import org.enso.interpreter.runtime.callable.atom.Atom;
import org.enso.interpreter.runtime.callable.function.Function;

/** An abstract representation of a case expression. */
public abstract class CaseNode extends BaseNode {
  /**
   * Executes the case expression.
   *
   * @param frame the stack frame in which to execute
   * @param target the constructor to destructure
   * @throws UnexpectedResultException when the result of desctructuring {@code target} can't be
   *     represented as a value of the expected return type
   */
  public abstract void executeAtom(VirtualFrame frame, Atom target)
      throws UnexpectedResultException;

  public abstract void executeFunction(VirtualFrame frame, Function target)
      throws UnexpectedResultException;

  public abstract void executeNumber(VirtualFrame frame, long target)
      throws UnexpectedResultException;
}
