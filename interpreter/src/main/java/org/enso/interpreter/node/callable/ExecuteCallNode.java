package org.enso.interpreter.node.callable;

import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import com.oracle.truffle.api.nodes.Node;
import org.enso.interpreter.runtime.callable.function.Function;

public abstract class ExecuteCallNode extends Node {
  @Specialization(guards = "function.getCallTarget() == cachedTarget")
  protected Object callDirect(
      Function function,
      Object[] arguments,
      @Cached("function.getCallTarget()") RootCallTarget cachedTarget,
      @Cached("create(cachedTarget)") DirectCallNode callNode) {
    return callNode.call(Function.ArgumentsHelper.buildArguments(function, arguments));
  }

  @Specialization(replaces = "callDirect")
  protected Object callIndirect(
      Function function, Object[] arguments, @Cached IndirectCallNode callNode) {
    return callNode.call(
        function.getCallTarget(), Function.ArgumentsHelper.buildArguments(function, arguments));
  }

  public abstract Object executeCall(Object receiver, Object[] arguments);
}
