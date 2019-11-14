package org.enso.interpreter.node.expression.builtin.debug;

import com.oracle.truffle.api.*;
import com.oracle.truffle.api.frame.FrameInstance;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.RootNode;
import org.enso.interpreter.Language;
import org.enso.interpreter.node.EnsoRootNode;
import org.enso.interpreter.node.expression.builtin.BuiltinRootNode;
import org.enso.interpreter.node.expression.debug.EvalNode;
import org.enso.interpreter.runtime.callable.CallerInfo;
import org.enso.interpreter.runtime.callable.argument.ArgumentDefinition;
import org.enso.interpreter.runtime.callable.argument.CallArgumentInfo;
import org.enso.interpreter.runtime.callable.function.Function;
import org.enso.interpreter.runtime.callable.function.FunctionSchema;
import org.enso.interpreter.runtime.scope.LocalScope;
import org.enso.interpreter.runtime.scope.ModuleScope;
import org.enso.interpreter.runtime.state.Stateful;

public class EvalFunNode extends BuiltinRootNode {
  private @Child EvalNode evalNode = EvalNode.build();

  public EvalFunNode(Language language) {
    super(language);
    evalNode.markTail();
  }

  @Override
  public Stateful execute(VirtualFrame frame) {
    CallerInfo callerInfo = Function.ArgumentsHelper.getCallerInfo(frame.getArguments());
    return evalNode.execute(
        callerInfo,
        Function.ArgumentsHelper.getState(frame.getArguments()),
        (String) Function.ArgumentsHelper.getPositionalArguments(frame.getArguments())[1]);
  }

  public static Function makeFunction(Language language) {
    return Function.fromBuiltinRootNodeWithFrameAccess(
        new EvalFunNode(language),
        FunctionSchema.CallStrategy.DIRECT_WHEN_TAIL,
        new ArgumentDefinition(0, "this", ArgumentDefinition.ExecutionMode.EXECUTE),
        new ArgumentDefinition(1, "expression", ArgumentDefinition.ExecutionMode.EXECUTE));
  }
}
