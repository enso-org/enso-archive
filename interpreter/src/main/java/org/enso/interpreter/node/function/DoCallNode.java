package org.enso.interpreter.node.function;

import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.NodeInfo;
import org.enso.interpreter.node.BaseNode;
import org.enso.interpreter.node.function.dispatch.DispatchNode;
import org.enso.interpreter.node.function.dispatch.SimpleDispatchNode;
import org.enso.interpreter.optimiser.tco.TailCallException;
import org.enso.interpreter.runtime.error.NotInvokableException;
import org.enso.interpreter.runtime.function.Function;
import org.enso.interpreter.runtime.type.Atom;
import org.enso.interpreter.runtime.type.AtomConstructor;

@NodeInfo(shortName = "DoCall")
public abstract class DoCallNode extends BaseNode {
  @Child private DispatchNode dispatchNode;

  public DoCallNode() {
    this.dispatchNode = new SimpleDispatchNode();
  }

  @Specialization
  public Object invokeFunction(Function callable, Object[] arguments) {
    if (this.isTail()) {
      throw new TailCallException(callable, arguments);
    } else {
      return this.dispatchNode.executeDispatch(callable, arguments);
    }
  }

  @Specialization
  public Atom invokeConstructor(AtomConstructor callable, Object[] arguments) {
    return callable.newInstance(arguments);
  }

  @Fallback
  public Object invokeGeneric(Object callable, Object[] arguments) {
    throw new NotInvokableException(callable, this);
  }

  public abstract Object execute(Object callable, Object[] arguments);

}
