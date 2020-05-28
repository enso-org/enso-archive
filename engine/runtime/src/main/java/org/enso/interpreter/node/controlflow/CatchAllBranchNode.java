package org.enso.interpreter.node.controlflow;

import com.oracle.truffle.api.frame.FrameUtil;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.NodeInfo;
import com.oracle.truffle.api.nodes.UnexpectedResultException;
import org.enso.interpreter.node.ExpressionNode;
import org.enso.interpreter.node.callable.ExecuteCallNode;
import org.enso.interpreter.node.callable.ExecuteCallNodeGen;
import org.enso.interpreter.node.callable.function.CreateFunctionNode;
import org.enso.interpreter.runtime.callable.atom.Atom;
import org.enso.interpreter.runtime.callable.function.Function;
import org.enso.interpreter.runtime.type.TypesGen;

/**
 * This node represents an explicit catch-call case in a pattern match, as provided by the user. It
 * executes the catch-all case code.
 */
@NodeInfo(shortName = "Fallback", description = "An explicit fallback branch in a case expression")
public class CatchAllBranchNode extends BranchNode {
  @Child private ExpressionNode functionNode;
  @Child private ExecuteCallNode executeCallNode = ExecuteCallNodeGen.create();

  CatchAllBranchNode(CreateFunctionNode functionNode) {
    this.functionNode = functionNode;
  }

  /**
   * Creates a node to handle the case catch-call.
   *
   * @param functionNode the function to execute in this case
   * @return a fallback node
   */
  public static CatchAllBranchNode build(CreateFunctionNode functionNode) {
    return new CatchAllBranchNode(functionNode);
  }

  public void execute(VirtualFrame frame, Object target) {
    Function function = TypesGen.asFunction(functionNode.executeGeneric(frame));
    Object state = FrameUtil.getObjectSafe(frame, getStateFrameSlot());
    throw new BranchSelectedException(
        executeCallNode.executeCall(
            function, null, state, new Object[] {target})); // Note [Caller Info For Case Branches]
  }
}
