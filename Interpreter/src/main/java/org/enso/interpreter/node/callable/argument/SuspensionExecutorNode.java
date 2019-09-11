package org.enso.interpreter.node.callable.argument;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.NodeField;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import com.oracle.truffle.api.nodes.Node;
import org.enso.interpreter.Constants;
import org.enso.interpreter.node.callable.dispatch.LoopingCallOptimiserNode;
import org.enso.interpreter.optimiser.tco.TailCallException;
import org.enso.interpreter.runtime.callable.argument.Suspension;

/** Node responsible for executing (forcing) suspensions passed to it as runtime values. */
@NodeField(name = "isTail", type = Boolean.class)
public abstract class SuspensionExecutorNode extends Node {

  /**
   * Forces the suspension to its resulting value.
   *
   * @param suspension the suspension to force
   * @return the return value of this suspension
   */
  public abstract Object executeSuspension(Suspension suspension);

  protected abstract boolean getIsTail();

  @Specialization(
      guards = "callNode.getCallTarget() == suspension.getCallTarget()",
      limit = Constants.CacheSizes.SUSPENSION_EXECUTOR_NODE)
  protected Object doCached(
      Suspension suspension,
      @Cached("create(suspension.getCallTarget())") DirectCallNode callNode,
      @Cached("createLoopingOptimizerIfNeeded()")
          LoopingCallOptimiserNode loopingCallOptimiserNode) {
    if (getIsTail()) {
      return callNode.call(suspension.getScope());
    } else {
      try {
        return callNode.call(suspension.getScope());
      } catch (TailCallException e) {
        return loopingCallOptimiserNode.executeDispatch(e.getFunction(), e.getArguments());
      }
    }
  }

  @Specialization(replaces = "doCached")
  protected Object doUncached(
      Suspension suspension,
      @Cached IndirectCallNode callNode,
      @Cached("createLoopingOptimizerIfNeeded()")
          LoopingCallOptimiserNode loopingCallOptimiserNode) {
    try {
      return callNode.call(suspension.getCallTarget(), suspension.getScope());
    } catch (TailCallException e) {
      return loopingCallOptimiserNode.executeDispatch(e.getFunction(), e.getArguments());
    }
  }

  protected LoopingCallOptimiserNode createLoopingOptimizerIfNeeded() {
    return getIsTail() ? null : new LoopingCallOptimiserNode();
  }
}
