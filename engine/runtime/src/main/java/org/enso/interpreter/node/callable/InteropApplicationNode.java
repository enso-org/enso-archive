package org.enso.interpreter.node.callable;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.Node;
import org.enso.interpreter.Constants;
import org.enso.interpreter.node.callable.dispatch.InvokeFunctionNode;
import org.enso.interpreter.runtime.callable.argument.CallArgumentInfo;
import org.enso.interpreter.runtime.callable.function.Function;

@GenerateUncached
public abstract class InteropApplicationNode extends Node {
  public abstract Object execute(Function function, Object state, Object[] arguments);

  @ExplodeLoop
  InvokeFunctionNode buildSorter(int length) {
    CallArgumentInfo[] args = new CallArgumentInfo[length];
    for (int i = 0; i < length; i++) {
      args[i] = new CallArgumentInfo();
    }
    return InvokeFunctionNode.build(
        args,
        InvokeCallableNode.DefaultsExecutionMode.EXECUTE,
        InvokeCallableNode.ArgumentsExecutionMode.PRE_EXECUTED);
  }

  @Specialization(
      guards = "arguments.length == cachedArgsLength",
      limit = Constants.CacheSizes.FUNCTION_INTEROP_LIBRARY)
  Object callCached(
      Function function,
      Object state,
      Object[] arguments,
      @Cached(value = "arguments.length") int cachedArgsLength,
      @Cached(value = "buildSorter(cachedArgsLength)") InvokeFunctionNode sorterNode) {
    return sorterNode.execute(function, null, state, arguments).getValue();
  }

  @Specialization(replaces = "callCached")
  Object callUncached(Function function, Object state, Object[] arguments) {
    return callCached(function, state, arguments, arguments.length, buildSorter(arguments.length));
  }
}
