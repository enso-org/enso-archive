package org.enso.interpreter.node.function.argument;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.NodeInfo;
import org.enso.interpreter.node.BaseNode;
import org.enso.interpreter.node.function.argument.ArgumentMappingNode.CallArgumentInfo;
import org.enso.interpreter.runtime.Callable;
import org.enso.interpreter.runtime.error.NotInvokableException;
import org.enso.interpreter.runtime.function.Function;

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
    if (callable instanceof Callable) {
      Function actualCallable = (Function) callable;
      int[] order = ArgumentMappingNode.generateArgMapping(actualCallable, this.schema);
      return reorderArguments(order, arguments);
    } else {
      throw new NotInvokableException(callable, this);
    }
  }

  @ExplodeLoop
  public static Object[] reorderArguments(int[] order, Object[] args) {
    // TODO: This should be a number of defined args with holes and stuff.
    Object[] result = new Object[args.length];
    for (int i = 0; i < args.length; i++) {
      result[order[i]] = args[i];
    }
    return result;
  }

}
