package org.enso.interpreter.node.function;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.NodeInfo;
import java.util.Arrays;
import org.enso.interpreter.node.ExpressionNode;
import org.enso.interpreter.node.function.argument.ArgumentMappingNode;
import org.enso.interpreter.node.function.argument.ArgumentMappingNodeGen;
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
        Arrays.stream(callArguments)
            .map(ArgumentMappingNode.CallArgumentInfo::new)
            .toArray(CallArgumentInfo[]::new);

    this.argumentsMap = ArgumentMappingNodeGen.create(argSchema);
  }

  @Override
  public void markTail() {
    this.argumentsMap.markTail();
  }

  @Override
  public void setTail(boolean isTail) {
    this.argumentsMap.setTail(isTail);
  }

  @Override
  public void markNotTail() {
    this.argumentsMap.markNotTail();
  }

  @ExplodeLoop
  public Object[] computeArguments(VirtualFrame frame) {
    Object[] computedArguments = new Object[this.argExpressions.length];

    for (int i = 0; i < this.argExpressions.length; ++i) {
      computedArguments[i] = this.argExpressions[i].executeGeneric(frame);
    }

    return computedArguments;
  }

  public Object executeGeneric(VirtualFrame frame) {
    Object callableResult = this.callable.executeGeneric(frame);
    Object[] computedArguments = computeArguments(frame);
    return this.argumentsMap.execute(callableResult, computedArguments);
  }
}
