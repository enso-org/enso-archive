package org.enso.interpreter.instrument;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.FrameInstance;
import com.oracle.truffle.api.frame.FrameInstanceVisitor;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.*;
import com.oracle.truffle.api.nodes.Node;
import java.util.HashMap;
import java.util.Map;
import org.enso.interpreter.node.ExpressionNode;
import org.enso.interpreter.node.callable.FunctionCallInstrumentationNode;
import org.enso.interpreter.runtime.tag.IdentifiedTag;
import org.enso.interpreter.runtime.type.Types;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

/** An instrument for getting values from AST-identified expressions. */
@TruffleInstrument.Registration(
    id = IdExecutionInstrument.INSTRUMENT_ID,
    services = IdExecutionInstrument.class)
public class IdExecutionInstrument extends TruffleInstrument {
  public static final String INSTRUMENT_ID = "id-value-extractor";

  private Env env;

  /**
   * Initializes the instrument. Substitute for a constructor, called by the Truffle framework.
   *
   * @param env the instrumentation environment
   */
  @Override
  protected void onCreate(Env env) {
    env.registerService(this);
    this.env = env;
  }

  /** A class for notifications about functions being called in the course of execution. */
  public static class ExpressionCall {
    private final UUID expressionId;
    private final FunctionCallInstrumentationNode.FunctionCall call;

    /**
     * Creates an instance of this class.
     *
     * @param expressionId the expression id where function call was performed.
     * @param call the actual function call data.
     */
    public ExpressionCall(UUID expressionId, FunctionCallInstrumentationNode.FunctionCall call) {
      this.expressionId = expressionId;
      this.call = call;
    }

    /** @return the id of the node performing the function call. */
    public UUID getExpressionId() {
      return expressionId;
    }

    /** @return the function call metadata. */
    public FunctionCallInstrumentationNode.FunctionCall getCall() {
      return call;
    }
  }

  /** A class for notifications about identified expressions' values being computed. */
  public static class ExpressionValue {
    private final UUID expressionId;
    private final String type;
    private final Object value;
    private final FunctionCallInstrumentationNode.FunctionCall call;

    /**
     * Creates a new instance of this class.
     *
     * @param expressionId the id of the expression being computed.
     * @param type of the computed expression.
     * @param value the value returned by computing the expression.
     * @param call the function call data.
     */
    public ExpressionValue(
        UUID expressionId,
        String type,
        Object value,
        FunctionCallInstrumentationNode.FunctionCall call) {
      this.expressionId = expressionId;
      this.type = type;
      this.value = value;
      this.call = call;
    }

    /**
     * Creates a new instance of this class.
     *
     * @param expressionId the id of the expression being computed.
     * @param type of the computed expression.
     * @param value the value returned by computing the expression.
     */
    public ExpressionValue(UUID expressionId, String type, Object value) {
      this(expressionId, type, value, null);
    }

    /** @return the id of the expression computed. */
    public UUID getExpressionId() {
      return expressionId;
    }

    /** @return the computed type of the expression. */
    @CompilerDirectives.TruffleBoundary
    public Optional<String> getType() {
      return Optional.ofNullable(type);
    }

    /** @return the computed value of the expression. */
    public Object getValue() {
      return value;
    }

    /** @return the function call data. */
    public FunctionCallInstrumentationNode.FunctionCall getCall() {
      return call;
    }
  }

  /** The listener class used by this instrument. */
  private static class IdExecutionEventListener implements ExecutionEventListener {
    private final CallTarget entryCallTarget;
    private final Consumer<ExpressionCall> functionCallCallback;
    private final Consumer<ExpressionValue> valueCallback;
    private final Map<UUID, FunctionCallInstrumentationNode.FunctionCall> calls = new HashMap<>();

    /**
     * Creates a new listener.
     *
     * @param entryCallTarget the call target being observed.
     * @param functionCallCallback the consumer of function call events.
     * @param valueCallback the consumer of the node value events.
     */
    public IdExecutionEventListener(
        CallTarget entryCallTarget,
        Consumer<ExpressionCall> functionCallCallback,
        Consumer<ExpressionValue> valueCallback) {
      this.entryCallTarget = entryCallTarget;
      this.functionCallCallback = functionCallCallback;
      this.valueCallback = valueCallback;
    }

    @Override
    public void onEnter(EventContext context, VirtualFrame frame) {}

    /**
     * Triggered when a node (either a function call sentry or an identified expression) finishes
     * execution.
     *
     * @param context the event context.
     * @param frame the current execution frame.
     * @param result the result of executing the node this method was triggered for.
     */
    @Override
    public void onReturnValue(EventContext context, VirtualFrame frame, Object result) {
      if (!isTopFrame(entryCallTarget)) {
        return;
      }
      Node node = context.getInstrumentedNode();
      if (node instanceof FunctionCallInstrumentationNode
          && result instanceof FunctionCallInstrumentationNode.FunctionCall) {
        ExpressionCall expressionCall =
            new ExpressionCall(
                ((FunctionCallInstrumentationNode) node).getId(),
                (FunctionCallInstrumentationNode.FunctionCall) result);
        calls.put(expressionCall.getExpressionId(), expressionCall.getCall());
        functionCallCallback.accept(expressionCall);
      } else if (node instanceof ExpressionNode) {
        UUID nodeId = ((ExpressionNode) node).getId();
        valueCallback.accept(
            new ExpressionValue(
                nodeId, Types.getName(result).orElse(null), result, calls.get(nodeId)));
      }
    }

    @Override
    public void onReturnExceptional(
        EventContext context, VirtualFrame frame, Throwable exception) {}

    /**
     * Checks if we're not inside a recursive call, i.e. the {@link #entryCallTarget} only appears
     * in the stack trace once.
     *
     * @return {@code true} if it's not a recursive call, {@code false} otherwise.
     */
    private boolean isTopFrame(CallTarget entryCallTarget) {
      Object result =
          Truffle.getRuntime()
              .iterateFrames(
                  new FrameInstanceVisitor<Object>() {
                    boolean seenFirst = false;

                    @Override
                    public Object visitFrame(FrameInstance frameInstance) {
                      CallTarget ct = frameInstance.getCallTarget();
                      if (ct != entryCallTarget) {
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
  }

  /** Listener that overrides value of target node id. */
  private static class ValueOverrideListener implements ExecutionEventListener {
    private final UUID targetId;
    private final Object overrideValue;

    /**
     * Creates a new listener.
     *
     * @param targetId the id which return value will be overridden.
     * @param overrideValue the value to use as the override.
     */
    public ValueOverrideListener(UUID targetId, Object overrideValue) {
      this.targetId = targetId;
      this.overrideValue = overrideValue;
    }

    @Override
    public Object onUnwind(EventContext context, VirtualFrame frame, Object info) {
      return info;
    }

    @Override
    public void onEnter(EventContext context, VirtualFrame frame) {
      if (!isTopFrame()) {
        return;
      }
      if (isTargetNode(context.getInstrumentedNode())) {
        throw context.createUnwind(overrideValue);
      }
    }

    @Override
    public void onReturnValue(EventContext context, VirtualFrame frame, Object result) {}

    @Override
    public void onReturnExceptional(
        EventContext context, VirtualFrame frame, Throwable exception) {}

    /**
     * Checks if we're not inside a recursive call, i.e. the node with {@link #targetId} only
     * appears in the stack trace once.
     *
     * @return {@code true} if it's not a recursive call, {@code false} otherwise.
     */
    private boolean isTopFrame() {
      Object result =
          Truffle.getRuntime()
              .iterateFrames(
                  new FrameInstanceVisitor<Object>() {
                    boolean seenFirst = false;

                    @Override
                    public Object visitFrame(FrameInstance frameInstance) {
                      if (!isTargetNode(frameInstance.getCallNode())) {
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

    /** Checks if the node id is a {@link #targetId}. */
    private boolean isTargetNode(Node node) {
      UUID nodeId = null;
      if (node instanceof FunctionCallInstrumentationNode) {
        nodeId = ((FunctionCallInstrumentationNode) node).getId();
      } else if (node instanceof ExpressionNode) {
        nodeId = ((ExpressionNode) node).getId();
      }

      return targetId.equals(nodeId);
    }
  }

  /**
   * Attach a new listener to observe identified nodes within given function.
   *
   * @param entryCallTarget the call target being observed.
   * @param funSourceStart the source start of the observed range of ids.
   * @param funSourceLength the length of the observed source range.
   * @param valueCallback the consumer of the node value events.
   * @param functionCallCallback the consumer of function call events.
   * @return a reference to the attached event listener.
   */
  public EventBinding<ExecutionEventListener> bind(
      CallTarget entryCallTarget,
      int funSourceStart,
      int funSourceLength,
      Consumer<ExpressionValue> valueCallback,
      Consumer<ExpressionCall> functionCallCallback) {
    SourceSectionFilter filter =
        SourceSectionFilter.newBuilder()
            .tagIs(StandardTags.ExpressionTag.class, StandardTags.CallTag.class)
            .tagIs(IdentifiedTag.class)
            .indexIn(funSourceStart, funSourceLength)
            .build();

    EventBinding<ExecutionEventListener> binding =
        env.getInstrumenter()
            .attachExecutionEventListener(
                filter,
                new IdExecutionEventListener(entryCallTarget, functionCallCallback, valueCallback));
    return binding;
  }

  /**
   * Sets the return value of the target expression. Acts as a marker to interrupt the execution of
   * the requested expression and return the provided value.
   *
   * @param targetId the id which return value will be overridden.
   * @param value the value to use as the override.
   */
  public void setOverride(UUID targetId, Object value) {
    SourceSectionFilter filter =
        SourceSectionFilter.newBuilder()
            .tagIs(StandardTags.ExpressionTag.class, StandardTags.CallTag.class)
            .tagIs(IdentifiedTag.class)
            .build();
    env.getInstrumenter()
        .attachExecutionEventListener(filter, new ValueOverrideListener(targetId, value));
  }
}
