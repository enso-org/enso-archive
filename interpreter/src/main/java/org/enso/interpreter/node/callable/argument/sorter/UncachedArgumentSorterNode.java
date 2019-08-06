package org.enso.interpreter.node.callable.argument.sorter;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.nodes.NodeInfo;
import org.enso.interpreter.node.BaseNode;
import org.enso.interpreter.runtime.callable.argument.CallArgumentInfo;
import org.enso.interpreter.runtime.callable.function.Function;
import org.enso.interpreter.runtime.error.NotInvokableException;
import org.enso.interpreter.runtime.type.TypesGen;

@NodeInfo(shortName = "ArgumentMap")
public class UncachedArgumentSorterNode extends BaseNode {
  private @CompilationFinal(dimensions = 1) CallArgumentInfo[] schema;

  public UncachedArgumentSorterNode(CallArgumentInfo[] schema) {
    this.schema = schema;
  }

  public static UncachedArgumentSorterNode create(CallArgumentInfo[] schema) {
    return new UncachedArgumentSorterNode(schema);
  }

  public Object[] execute(Object callable, Object[] arguments, int numArgsDefinedForCallable) {
    if (TypesGen.isCallable(callable)) {
      Function actualCallable = (Function) callable;
      int[] order = CallArgumentInfo.generateArgMapping(actualCallable, this.schema);
      return CallArgumentInfo.reorderArguments(order, arguments, numArgsDefinedForCallable);
    } else {
      throw new NotInvokableException(callable, this);
    }
  }
}
