package org.enso.interpreter.node.callable.argument.sorter;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.NodeInfo;
import java.util.Arrays;
import org.enso.interpreter.node.BaseNode;
import org.enso.interpreter.runtime.callable.Callable;
import org.enso.interpreter.runtime.callable.argument.CallArgumentInfo;
import org.enso.interpreter.runtime.error.NotInvokableException;

@NodeInfo(shortName = "ArgMap")
public abstract class ArgumentSorterNode extends BaseNode {
  private @CompilationFinal(dimensions = 1) CallArgumentInfo[] schema;
  private @CompilationFinal boolean isFullyPositional;

  public ArgumentSorterNode(CallArgumentInfo[] schema) {
    this.schema = schema;
    this.isFullyPositional = Arrays.stream(schema).allMatch(CallArgumentInfo::isPositional);
  }

  @Specialization(guards = "isFullyPositional()")
  public Object[] invokePositional(Object callable, Object[] arguments) {
    CompilerDirectives.ensureVirtualizedHere(arguments);
    return arguments;
  }

  @Specialization(guards = "mappingNode.hasSameCallable(callable)")
  @ExplodeLoop
  public Object[] invokeCached(
      Callable callable,
      Object[] arguments,
      @Cached("create(callable, getSchema())") CachedArgumentSorterNode mappingNode) {
    return mappingNode.execute(arguments, callable.getArgs().length);
  }

  @Specialization
  public Object[] invokeUncached(
      Callable callable,
      Object[] arguments,
      @Cached("create(getSchema())") UncachedArgumentSorterNode mappingNode) {
    return mappingNode.execute(callable, arguments, callable.getArgs().length);
  }

  @Fallback
  public Object[] invokeGeneric(Object callable, Object[] arguments) {
    throw new NotInvokableException(callable, this);
  }

  public abstract Object[] execute(Object callable, Object[] arguments);

  public CallArgumentInfo[] getSchema() {
    return schema;
  }

  public boolean isFullyPositional() {
    return isFullyPositional;
  }
}
