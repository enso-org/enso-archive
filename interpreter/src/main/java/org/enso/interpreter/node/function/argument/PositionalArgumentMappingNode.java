package org.enso.interpreter.node.function.argument;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import org.enso.interpreter.node.BaseNode;
import org.enso.interpreter.node.function.dispatch.DispatchNode;
import org.enso.interpreter.node.function.dispatch.SimpleDispatchNode;
import org.enso.interpreter.optimiser.tco.TailCallException;
import org.enso.interpreter.runtime.error.NotInvokableException;
import org.enso.interpreter.runtime.function.Function;
import org.enso.interpreter.runtime.type.AtomConstructor;

public class PositionalArgumentMappingNode extends BaseNode {
  @Child private DispatchNode dispatchNode;

  public PositionalArgumentMappingNode(DispatchNode dispatchNode, boolean isTailCall) {
    this.dispatchNode = dispatchNode;
    setTail(isTailCall);
  }

  public static PositionalArgumentMappingNode create(boolean isTailCall) {
    return new PositionalArgumentMappingNode(new SimpleDispatchNode(), isTailCall);
  }

  public Object execute(Object callable, Object[] arguments) {
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
