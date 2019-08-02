package org.enso.interpreter.node.function.argument;

import org.enso.interpreter.node.BaseNode;

public abstract class ArgumentMappingNode extends BaseNode {
  public abstract Object execute(Object callable, Object[] arguments);
}
