package org.enso.interpreter.node.function.argument.mapping;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.NodeInfo;
import java.util.Arrays;
import org.enso.interpreter.node.BaseNode;
import org.enso.interpreter.node.function.DoCallNode;
import org.enso.interpreter.node.function.DoCallNodeGen;
import org.enso.interpreter.runtime.function.argument.CallArgumentInfo;

@NodeInfo(shortName = "ArgMap")
public abstract class ArgumentMappingNode extends BaseNode {
  private @CompilationFinal(dimensions = 1) CallArgumentInfo[] schema;
  private @CompilationFinal boolean isFullyPositional;
  @Child private DoCallNode doCallNode;

  public ArgumentMappingNode(CallArgumentInfo[] schema) {
    this.schema = schema;
    this.isFullyPositional = Arrays.stream(schema).allMatch(CallArgumentInfo::isPositional);
    this.doCallNode = DoCallNodeGen.create();
  }

  @Override
  public void markTail() {
    this.doCallNode.markTail();
  }

  @Override
  public void markNotTail() {
    this.doCallNode.markNotTail();
  }

  @Override
  public void setTail(boolean isTail) {
    this.doCallNode.setTail(isTail);
  }

  @Specialization(guards = "isFullyPositional()")
  public Object invokePositional(Object callable, Object[] arguments) {
    CompilerDirectives.ensureVirtualizedHere(arguments);
    return this.doCallNode.execute(callable, arguments);
  }

  @Specialization(guards = "mappingNode.hasSameCallable(callable)")
  @ExplodeLoop
  public Object invokeCached(
      Object callable,
      Object[] arguments,
      @Cached("create(callable, getSchema())") CachedArgumentMappingNode mappingNode ) {
    arguments = mappingNode.execute(arguments);

    return this.doCallNode.execute(callable, arguments);
  }

  @Specialization
  public Object invokeUncached(
      Object callable,
      Object[] arguments,
      @Cached("create(getSchema())") UncachedArgumentMappingNode mappingNode) {
    arguments = mappingNode.execute(callable, arguments);

    return this.doCallNode.execute(callable, arguments);
  }

  public abstract Object execute(Object callable, Object[] arguments);

  public CallArgumentInfo[] getSchema() {
    return schema;
  }

  public boolean isFullyPositional() {
    return isFullyPositional;
  }

}
