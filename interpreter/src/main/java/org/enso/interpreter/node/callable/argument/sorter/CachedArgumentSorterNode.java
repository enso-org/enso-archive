package org.enso.interpreter.node.callable.argument.sorter;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import org.enso.interpreter.node.BaseNode;
import org.enso.interpreter.runtime.callable.argument.CallArgumentInfo;
import org.enso.interpreter.runtime.callable.function.Function;
import org.enso.interpreter.runtime.type.TypesGen;

public class CachedArgumentSorterNode extends BaseNode {
  private @CompilationFinal Object callable;
  private @CompilationFinal(dimensions = 1) int[] mapping;

  public CachedArgumentSorterNode(Object callable, CallArgumentInfo[] schema) {
    this.callable = callable;
    this.mapping = CallArgumentInfo.generateArgMapping(callable, schema);
  }

  public static CachedArgumentSorterNode create(Object callable, CallArgumentInfo[] schema) {
    return new CachedArgumentSorterNode(callable, schema);
  }

  @ExplodeLoop
  public Object[] execute(Object[] arguments, int numArgsDefinedForCallable) {
    return CallArgumentInfo.reorderArguments(this.mapping, arguments, numArgsDefinedForCallable);
  }

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
