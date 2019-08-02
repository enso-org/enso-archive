package org.enso.interpreter.node.function;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.NodeInfo;
import java.util.Arrays;
import java.util.Map;
import org.enso.interpreter.node.ExpressionNode;
import org.enso.interpreter.node.function.argument.ArgumentMappingNode;
import org.enso.interpreter.node.function.argument.ArgumentMappingNode.CallArgumentInfo;
import org.enso.interpreter.runtime.function.argument.CallArgument;

@NodeInfo(shortName = "@", description = "Executes function")
public class InvokeNode extends ExpressionNode {
  @Children private final ExpressionNode[] argExpressions;
  @Child private ArgumentMappingNode argumentsMap;
  @Child private ExpressionNode callable;

  public InvokeNode(ExpressionNode callable, CallArgument[] callArguments) {
    this.callable = callable;
    this.argExpressions =
        Arrays.stream(callArguments)
            .map(CallArgument::getExpression)
            .toArray(ExpressionNode[]::new);

    CallArgumentInfo[] argSchema =
        Arrays.stream(callArguments, 0, callArguments.length)
            .map(CallArgumentInfo::new)
            .toArray(CallArgumentInfo[]::new);

    this.argumentsMap = new ArgumentMappingNode(argSchema);
  }

  /**
   * Looks up the argument by the provided key type in the appropriate map.
   *
   * <p>This method exists because the lookups need to take place in the interpreter (behind the
   * truffle boundary). If they do not, then the partial evaluator tries to inline the map lookups
   * to a significant depth.
   *
   * @param map The map in which to look up the key.
   * @param key The key to use for lookup.
   * @param <K> The key type of the map.
   * @return `true` if the key exists, otherwise `false`.
   */
  @TruffleBoundary
  public static <K> boolean hasArgByKey(Map<K, Integer> map, K key) {
    return map.containsKey(key);
  }

  @ExplodeLoop
  public Object[] computeArguments(VirtualFrame frame) {
    Object[] computedArguments = new Object[this.argExpressions.length];

    for (int i = 0; i < this.argExpressions.length; ++i) {
      computedArguments[i] = this.argExpressions[i].executeGeneric(frame);
    }

    return computedArguments;
  }

  @Override
  public void markTail() {
    this.argumentsMap.markTail();
  }

  public Object executeGeneric(VirtualFrame frame) {
    Object callableResult = this.callable.executeGeneric(frame);
    Object[] computedArguments = computeArguments(frame);
    return this.argumentsMap.execute(callableResult, computedArguments);
  }
}
