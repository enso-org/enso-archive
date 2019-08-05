package org.enso.interpreter.node.function.argument.mapping;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import org.enso.interpreter.node.BaseNode;
import org.enso.interpreter.runtime.TypesGen;
import org.enso.interpreter.runtime.function.Function;
import org.enso.interpreter.runtime.function.argument.CallArgumentInfo;

public class CachedArgumentMappingNode extends BaseNode {
  private @CompilationFinal Object callable;
  private @CompilationFinal(dimensions = 1) int[] mapping;

  public CachedArgumentMappingNode(Object callable, CallArgumentInfo[] schema) {
    this.callable = callable;
    this.mapping = CallArgumentInfo.generateArgMapping(callable, schema, null);
  }

  public static CachedArgumentMappingNode create(Object callable, CallArgumentInfo[] schema) {
    return new CachedArgumentMappingNode(callable, schema);
  }

  @ExplodeLoop
  public Object[] execute(Object[] arguments) {
    return CallArgumentInfo.reorderArguments(this.mapping, arguments);
  }

  @SuppressWarnings("unused") // Used in annotations
  public boolean hasSameCallable(Object other) {
    if (TypesGen.isAtomConstructor(other)) {
      return this.callable == other;
    } else if (TypesGen.isFunction(this.callable)) {
      if (TypesGen.isFunction(other)) {
        return ((Function) this.callable).getCallTarget() == ((Function) other).getCallTarget();
      }
    }
    return false;
  }
}
