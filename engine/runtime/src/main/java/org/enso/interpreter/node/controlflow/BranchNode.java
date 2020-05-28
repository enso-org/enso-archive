package org.enso.interpreter.node.controlflow;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.NodeInfo;
import com.oracle.truffle.api.nodes.UnexpectedResultException;
import org.enso.interpreter.node.BaseNode;
import org.enso.interpreter.runtime.callable.atom.Atom;
import org.enso.interpreter.runtime.callable.function.Function;

/** An abstract representation of a case branch. */
@NodeInfo(shortName = "case_branch", description = "Represents a case branch at runtime.")
public abstract class BranchNode extends BaseNode {

  /**
   * Executes the case expression on an
   *
   * @param frame the stack frame in which to execute
   * @param target the number to match
   * @throws UnexpectedResultException when the result of desctructuring {@code target} can't be
   *     represented as a value of the expected return type
   */
  public abstract Object execute(VirtualFrame frame, Object target);
}
