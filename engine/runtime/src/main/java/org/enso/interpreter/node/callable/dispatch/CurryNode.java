package org.enso.interpreter.node.callable.dispatch;

import cats.data.Func;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.VirtualFrame;
import org.enso.interpreter.node.BaseNode;
import org.enso.interpreter.node.callable.CaptureCallerInfoNode;
import org.enso.interpreter.node.callable.ExecuteCallNode;
import org.enso.interpreter.node.callable.InvokeCallableNode;
import org.enso.interpreter.node.callable.InvokeCallableNodeGen;
import org.enso.interpreter.runtime.callable.CallerInfo;
import org.enso.interpreter.runtime.callable.function.Function;
import org.enso.interpreter.runtime.callable.function.FunctionSchema;
import org.enso.interpreter.runtime.control.TailCallException;
import org.enso.interpreter.runtime.state.Stateful;

/** Handles runtime function currying and oversaturated (eta-expanded) calls. */
public class CurryNode extends BaseNode {
  private final FunctionSchema preApplicationSchema;
  private final FunctionSchema postApplicationSchema;
  private final boolean appliesFully;
  private @Child InvokeCallableNode oversaturatedCallableNode;
  private @Child ExecuteCallNode directCall;
  private @Child CallOptimiserNode loopingCall;
  private @Child CaptureCallerInfoNode captureCallerInfoNode;

  private CurryNode(
      FunctionSchema originalSchema,
      FunctionSchema postApplicationSchema,
      InvokeCallableNode.DefaultsExecutionMode defaultsExecutionMode,
      InvokeCallableNode.ArgumentsExecutionMode argumentsExecutionMode,
      boolean isTail) {
    setTail(isTail);
    this.preApplicationSchema = originalSchema;
    this.postApplicationSchema = postApplicationSchema;
    appliesFully = isFunctionFullyApplied(defaultsExecutionMode);
    if (preApplicationSchema.getCallerFrameAccess().shouldFrameBePassed()) {
      this.captureCallerInfoNode = CaptureCallerInfoNode.build();
    }
    initializeCallNodes();
    initializeOversaturatedCallNode(defaultsExecutionMode, argumentsExecutionMode);
  }

  private void initializeCallNodes() {
    if (postApplicationSchema.hasOversaturatedArgs()
        || !preApplicationSchema.getCallStrategy().shouldCallDirect(isTail())) {
      this.loopingCall = CallOptimiserNode.build();
    } else {
      this.directCall = ExecuteCallNode.build();
    }
  }

  private void initializeOversaturatedCallNode(
      InvokeCallableNode.DefaultsExecutionMode defaultsExecutionMode,
      InvokeCallableNode.ArgumentsExecutionMode argumentsExecutionMode) {
    if (postApplicationSchema.hasOversaturatedArgs()) {
      oversaturatedCallableNode =
          InvokeCallableNodeGen.create(
              postApplicationSchema.getOversaturatedArguments(),
              defaultsExecutionMode,
              argumentsExecutionMode);
      oversaturatedCallableNode.setTail(isTail());
    }
  }

  public static CurryNode build(
      FunctionSchema preApplicationSchema,
      FunctionSchema postApplicationSchema,
      InvokeCallableNode.DefaultsExecutionMode defaultsExecutionMode,
      InvokeCallableNode.ArgumentsExecutionMode argumentsExecutionMode,
      boolean isTail) {
    return new CurryNode(
        preApplicationSchema,
        postApplicationSchema,
        defaultsExecutionMode,
        argumentsExecutionMode,
        isTail);
  }

  public Stateful execute(
      VirtualFrame frame,
      Function function,
      Object state,
      Object[] arguments,
      Object[] oversaturatedArguments) {
    CallerInfo callerInfo = null;
    if (captureCallerInfoNode != null) {
      callerInfo = captureCallerInfoNode.execute(frame);
    }
    if (appliesFully) {
      if (!postApplicationSchema.hasOversaturatedArgs()) {
        return doCall(function, callerInfo, state, arguments);
      } else {
        Stateful evaluatedVal = loopingCall.executeDispatch(function, callerInfo, state, arguments);

        return this.oversaturatedCallableNode.execute(
            evaluatedVal.getValue(), frame, evaluatedVal.getState(), oversaturatedArguments);
      }
    } else {
      return new Stateful(
          state,
          new Function(
              function.getCallTarget(),
              function.getScope(),
              postApplicationSchema,
              arguments,
              oversaturatedArguments));
    }
  }

  private Stateful doCall(
      Function function, CallerInfo callerInfo, Object state, Object[] arguments) {
    if (preApplicationSchema.getCallStrategy().shouldCallDirect(isTail())) {
      return directCall.executeCall(function, callerInfo, state, arguments);
    } else if (isTail()) {
      throw new TailCallException(function, callerInfo, state, arguments);
    } else {
      return loopingCall.executeDispatch(function, callerInfo, state, arguments);
    }
  }

  private boolean isFunctionFullyApplied(
      InvokeCallableNode.DefaultsExecutionMode defaultsExecutionMode) {
    boolean functionIsFullyApplied = true;
    for (int i = 0; i < postApplicationSchema.getArgumentsCount(); i++) {
      boolean hasValidDefault =
          postApplicationSchema.hasDefaultAt(i) && !defaultsExecutionMode.isIgnore();
      boolean hasPreappliedArg = postApplicationSchema.hasPreAppliedAt(i);

      if (!(hasValidDefault || hasPreappliedArg)) {
        functionIsFullyApplied = false;
        break;
      }
    }
    return functionIsFullyApplied;
  }
}
