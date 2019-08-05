package org.enso.interpreter.node.function.argument.mapping;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.nodes.NodeInfo;
import org.enso.interpreter.node.BaseNode;
import org.enso.interpreter.runtime.TypesGen;
import org.enso.interpreter.runtime.error.NotInvokableException;
import org.enso.interpreter.runtime.function.Function;
import org.enso.interpreter.runtime.function.argument.CallArgumentInfo;

@NodeInfo(shortName = "ArgumentMap")
public class UncachedArgumentMappingNode extends BaseNode {
  private @CompilationFinal(dimensions = 1) CallArgumentInfo[] schema;

  public UncachedArgumentMappingNode(CallArgumentInfo[] schema) {
    this.schema = schema;
  }

  public static UncachedArgumentMappingNode create(CallArgumentInfo[] schema) {
    return new UncachedArgumentMappingNode(schema);
  }

  public Object[] execute(Object callable, Object[] arguments) {
    if (TypesGen.isCallable(callable)) {
      Function actualCallable = (Function) callable;
      int[] order = CallArgumentInfo.generateArgMapping(actualCallable, this.schema, this);
      return CallArgumentInfo.reorderArguments(order, arguments);
    } else {
      throw new NotInvokableException(callable, this);
    }
  }

}
