package org.enso.interpreter.node.function.argument;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import org.enso.interpreter.node.BaseNode;
import org.enso.interpreter.node.function.argument.ArgumentMappingNode.CallArgumentInfo;

public class CachedArgumentMappingNode extends BaseNode {
  private final Object callable;
  private @CompilationFinal(dimensions = 1) int[] mapping;

  public CachedArgumentMappingNode(Object callable, CallArgumentInfo[] schema) {
    this.callable = callable;
    this.mapping = ArgumentMappingNode.generateArgMapping(callable, schema);
  }

  public static CachedArgumentMappingNode create(Object callable, CallArgumentInfo[] schema) {
    return new CachedArgumentMappingNode(callable, schema);
  }

  @ExplodeLoop
  public Object[] execute(Object[] arguments) {
    // TODO: This should be a number of defined args with holes and stuff.
    Object[] result = new Object[arguments.length];
    for (int i = 0; i < arguments.length; i++) {
      result[this.mapping[i]] = arguments[i];
    }
    return result;
  }
}
