package org.enso.interpreter.node.function.argument;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import java.util.Arrays;
import org.enso.interpreter.node.BaseNode;
import org.enso.interpreter.node.function.argument.UncachedArgumentMappingNode.CallArgumentInfo;
import org.enso.interpreter.node.function.dispatch.DispatchNode;
import org.enso.interpreter.node.function.dispatch.SimpleDispatchNode;
import org.enso.interpreter.optimiser.tco.TailCallException;
import org.enso.interpreter.runtime.error.NotInvokableException;
import org.enso.interpreter.runtime.function.Function;
import org.enso.interpreter.runtime.type.AtomConstructor;

public abstract class ArgumentMappingNode extends BaseNode {
  private final CallArgumentInfo[] schema;
  private final boolean isFullyPositional;
  @Child private DispatchNode dispatchNode;

  public ArgumentMappingNode(CallArgumentInfo[] schema) {
    this.schema = schema;
    this.isFullyPositional = Arrays.stream(schema).allMatch(CallArgumentInfo::isPositional);
    this.dispatchNode = new SimpleDispatchNode();
  }

  @Specialization(guards = "isFullyPositional()")
  public Object invokePositional(Object callable, Object[] arguments) {
    if (callable instanceof Function) {
      Function actualCallable = (Function) callable;
      if (this.isTail()) {
        throw new TailCallException(actualCallable, arguments);
      } else {
        return dispatchNode.executeDispatch(actualCallable, arguments);
      }

    } else if (callable instanceof AtomConstructor) {
      AtomConstructor actualCallable = (AtomConstructor) callable;
      return actualCallable.newInstance(arguments);

    } else {
      throw new NotInvokableException(callable, this);
    }
  }

  @Specialization
  public Object invokeUncached(
      Object callable,
      Object[] arguments,
      @Cached("create(getSchema())") UncachedArgumentMappingNode mappingNode) {
    Object[] remappedArgs = mappingNode.execute(callable, arguments);
    return doCall(callable, remappedArgs);
  }

  public abstract Object execute(Object callable, Object[] arguments);

  public CallArgumentInfo[] getSchema() {
    return schema;
  }

  public boolean isFullyPositional() {
    return isFullyPositional;
  }

  private Object doCall(Object callable, Object[] arguments) {
    if (callable instanceof Function) {
      Function actualCallable = (Function) callable;
      if (this.isTail()) {
        throw new TailCallException(actualCallable, arguments);
      } else {
        return dispatchNode.executeDispatch(actualCallable, arguments);
      }

    } else if (callable instanceof AtomConstructor) {
      AtomConstructor actualCallable = (AtomConstructor) callable;
      return actualCallable.newInstance(arguments);

    } else {
      throw new NotInvokableException(callable, this);
    }
  }
}
