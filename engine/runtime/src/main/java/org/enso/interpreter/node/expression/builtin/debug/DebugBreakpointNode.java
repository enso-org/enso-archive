package org.enso.interpreter.node.expression.builtin.debug;

import com.oracle.truffle.api.frame.VirtualFrame;
import org.enso.interpreter.Constants;
import org.enso.interpreter.Language;
import org.enso.interpreter.node.expression.builtin.BuiltinRootNode;
import org.enso.interpreter.node.expression.debug.BreakpointNode;
import org.enso.interpreter.runtime.callable.argument.ArgumentDefinition;
import org.enso.interpreter.runtime.callable.function.Function;
import org.enso.interpreter.runtime.callable.function.FunctionSchema;
import org.enso.interpreter.runtime.state.Stateful;

public class DebugBreakpointNode extends BuiltinRootNode {
  private @Child BreakpointNode instrumentableNode = BreakpointNode.build();

  private DebugBreakpointNode(Language language) {
    super(language);
  }

  @Override
  public Stateful execute(VirtualFrame frame) {
    Object state = Function.ArgumentsHelper.getState(frame.getArguments());
    return instrumentableNode.execute(frame, state);
  }

  public static Function makeFunction(Language language) {
    return Function.fromBuiltinRootNodeWithCallerFrameAccess(
        new DebugBreakpointNode(language),
        FunctionSchema.CallStrategy.ALWAYS_DIRECT,
        new ArgumentDefinition(
            0, Constants.THIS_ARGUMENT_NAME, ArgumentDefinition.ExecutionMode.EXECUTE));
  }
}
