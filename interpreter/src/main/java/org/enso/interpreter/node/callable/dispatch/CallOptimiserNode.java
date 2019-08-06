package org.enso.interpreter.node.callable.dispatch;

import com.oracle.truffle.api.nodes.Node;

public abstract class CallOptimiserNode extends Node {
  public abstract Object executeDispatch(Object receiver, Object[] arguments);
}
