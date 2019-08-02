package org.enso.interpreter.node.function.argument;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import java.util.Arrays;
import org.enso.interpreter.node.BaseNode;
import org.enso.interpreter.node.function.argument.UncachedArgumentMappingNode.CallArgumentInfo;

public abstract class ArgumentMappingNode extends BaseNode {
  private final CallArgumentInfo[] schema;
  private final boolean isFullyPositional;

  @Child private PositionalArgumentMappingNode positionalArgumentMappingNode;
  @Child private UncachedArgumentMappingNode uncachedArgumentMappingNode;

  public ArgumentMappingNode(CallArgumentInfo[] schema) {
    this.schema = schema;
    this.isFullyPositional = Arrays.stream(schema).allMatch(CallArgumentInfo::isPositional);
    positionalArgumentMappingNode = PositionalArgumentMappingNode.create(isTail());
    uncachedArgumentMappingNode = UncachedArgumentMappingNode.create(schema, isTail());
  }

  @Override
  public void markTail() {
    positionalArgumentMappingNode.markTail();
  }

  @Specialization(guards = "isFullyPositional()")
  public Object invokePositional(Object callable, Object[] arguments) {
    return positionalArgumentMappingNode.execute(callable, arguments);
  }

  @Specialization
  public Object invokeUncached(Object callable, Object[] arguments) {
    return uncachedArgumentMappingNode.execute(callable, arguments);
  }

  public abstract Object execute(Object callable, Object[] arguments);

  public CallArgumentInfo[] getSchema() {
    return schema;
  }

  public boolean isFullyPositional() {
    return isFullyPositional;
  }
}
