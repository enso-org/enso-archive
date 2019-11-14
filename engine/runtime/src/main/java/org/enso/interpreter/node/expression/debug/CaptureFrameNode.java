package org.enso.interpreter.node.expression.debug;

import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.frame.VirtualFrame;
import org.enso.interpreter.node.ExpressionNode;

public class CaptureFrameNode extends ExpressionNode {
  public static class WithFrame {
    private final MaterializedFrame frame;
    private final Object result;

    public WithFrame(MaterializedFrame frame, Object result) {
      this.frame = frame;
      this.result = result;
    }

    public MaterializedFrame getFrame() {
      return frame;
    }

    public Object getResult() {
      return result;
    }
  }

  private @Child ExpressionNode expression;

  public CaptureFrameNode(ExpressionNode expression) {
    this.expression = expression;
  }

  @Override
  public Object executeGeneric(VirtualFrame frame) {
    return new WithFrame(frame.materialize(), expression.executeGeneric(frame));
  }
}
