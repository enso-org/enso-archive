package org.enso.interpreter.node.function;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;

public abstract class CallNode extends Node {
  public abstract Object doCall(VirtualFrame frame, Object receiver, Object[] arguments);
}
