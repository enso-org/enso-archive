package org.enso.interpreter.node.expression.debug;

import com.oracle.truffle.api.frame.VirtualFrame;
import org.enso.interpreter.node.ExpressionNode;
import org.enso.interpreter.node.callable.CaptureCallerInfoNode;
import org.enso.interpreter.runtime.callable.CallerInfo;

public class CaptureResultScope extends ExpressionNode {
  public static class WithCallerInfo {
    private final CallerInfo callerInfo;
    private final Object result;

    private WithCallerInfo(CallerInfo callerInfo, Object result) {
      this.callerInfo = callerInfo;
      this.result = result;
    }

    public CallerInfo getCallerInfo() {
      return callerInfo;
    }

    public Object getResult() {
      return result;
    }
  }

  private @Child ExpressionNode expression;
  private @Child CaptureCallerInfoNode captureCallerInfoNode = CaptureCallerInfoNode.build();

  private CaptureResultScope(ExpressionNode expression) {
    this.expression = expression;
  }

  public static CaptureResultScope build(ExpressionNode expressionNode) {
    return new CaptureResultScope(expressionNode);
  }

  @Override
  public Object executeGeneric(VirtualFrame frame) {
    return new WithCallerInfo(
        captureCallerInfoNode.execute(frame), expression.executeGeneric(frame));
  }
}
