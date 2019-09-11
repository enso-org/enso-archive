package org.enso.interpreter.node.callable;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import org.enso.interpreter.node.ExpressionNode;
import org.enso.interpreter.node.callable.argument.SuspensionExecutorNode;
import org.enso.interpreter.node.callable.argument.SuspensionExecutorNodeGen;
import org.enso.interpreter.node.callable.dispatch.LoopingCallOptimiserNode;
import org.enso.interpreter.optimiser.tco.TailCallException;
import org.enso.interpreter.runtime.callable.argument.Suspension;

/** Node responsible for handling user-requested suspensions forcing. */
@NodeChild(value = "target", type = ExpressionNode.class)
public abstract class ForceNode extends ExpressionNode {
  @Specialization
  protected Object passToExecutorNode(
      Suspension suspension,
      @Cached("createSuspensionExecutor()") SuspensionExecutorNode suspensionExecutorNode) {
    return suspensionExecutorNode.executeSuspension(suspension);
  }

  protected SuspensionExecutorNode createSuspensionExecutor() {
    return SuspensionExecutorNodeGen.create(isTail());
  }
}
