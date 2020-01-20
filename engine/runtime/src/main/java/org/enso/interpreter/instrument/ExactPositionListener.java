package org.enso.interpreter.instrument;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.FrameInstance;
import com.oracle.truffle.api.frame.FrameInstanceVisitor;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.EventBinding;
import com.oracle.truffle.api.instrumentation.EventContext;
import com.oracle.truffle.api.instrumentation.ExecutionEventListener;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.source.SourceSection;

public abstract class ExactPositionListener implements ExecutionEventListener {
  private EventBinding<ExactPositionListener> binding;
  private final int start;
  private final int length;
  private final String funName;

  public ExactPositionListener(String funName, int start, int length) {
    this.funName = funName;
    this.start = start;
    this.length = length;
  }

  public void setBinding(EventBinding<ExactPositionListener> binding) {
    this.binding = binding;
  }

  private boolean isTopFrame() {
    Object result =
        Truffle.getRuntime()
            .iterateFrames(
                new FrameInstanceVisitor<Object>() {
                  boolean seenFirst = false;

                  @Override
                  public Object visitFrame(FrameInstance frameInstance) {
                    CallTarget ct = frameInstance.getCallTarget();
                    if (ct instanceof RootCallTarget
                        && !funName.equals(((RootCallTarget) ct).getRootNode().getName())) {
                      return null;
                    }
                    if (seenFirst) {
                      return new Object();
                    } else {
                      seenFirst = true;
                      return null;
                    }
                  }
                });
    return result == null;
  }

  public abstract void handleReturnValue(Object result);

  /**
   * Get the start location of the nodes expected by this listener.
   *
   * @return the start location for this listener
   */
  public int getStart() {
    return start;
  }

  /**
   * Get the source length of the nodes expected by this listener.
   *
   * @return the source length for this listener
   */
  public int getLength() {
    return length;
  }

  /**
   * Was a node with parameters specified for this listener encountered in the course of execution?
   *
   * @return {@code true} if the requested node was observed, {@code false} otherwise
   */
  public boolean isSuccessful() {
    return binding.isDisposed();
  }

  @Override
  public void onEnter(EventContext context, VirtualFrame frame) {}

  /**
   * Checks if the node to be executed is the node this listener was created to observe and triggers
   * the callback if the correct node just finished executing.
   *
   * @param context current execution context
   * @param frame current execution frame
   * @param result the return value of the currently executed node
   */
  @Override
  public void onReturnValue(EventContext context, VirtualFrame frame, Object result) {
    if (!isTopFrame()) {
      return;
    }
    Node node = context.getInstrumentedNode();
    SourceSection section = node.getSourceSection();
    if (section == null || !section.hasCharIndex()) {
      return;
    }
    if (section.getCharIndex() == start && section.getCharLength() == length) {
      binding.dispose();
      handleReturnValue(result);
    }
  }

  @Override
  public void onReturnExceptional(EventContext context, VirtualFrame frame, Throwable exception) {}
}
