package org.enso.interpreter.instrument;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.FrameInstance;
import com.oracle.truffle.api.frame.FrameInstanceVisitor;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.*;
import com.oracle.truffle.api.nodes.Node;
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
    private final Consumer<ExpressionValue> valueCallback;
    private final Consumer<ExpressionValue> visualisationCallback;
    private final RuntimeCache cache;

    /**
     * Creates a new listener.
     *
     * @param entryCallTarget the call target being observed.
     * @param cache the precomputed expression values.
     * @param valueCallback the consumer of the node value events.
     * @param visualisationCallback the consumer of the node visualisation events.
     */
    public IdExecutionEventListener(
        CallTarget entryCallTarget,
        RuntimeCache cache,
        Consumer<ExpressionValue> valueCallback,
        Consumer<ExpressionValue> visualisationCallback) {
      this.entryCallTarget = entryCallTarget;
      this.cache = cache;
      this.valueCallback = valueCallback;
      this.visualisationCallback = visualisationCallback;
    }

    @Override
    public Object onUnwind(EventContext context, VirtualFrame frame, Object info) {
      return info;
    }

    @Override
    public void onEnter(EventContext context, VirtualFrame frame) {
      if (!isTopFrame(entryCallTarget)) {
        return;
      }

      Node node = context.getInstrumentedNode();

      UUID nodeId = null;
      if (node instanceof ExpressionNode) {
        nodeId = ((ExpressionNode) node).getId();
      } else if (node instanceof FunctionCallInstrumentationNode) {
        nodeId = ((FunctionCallInstrumentationNode) node).getId();
      }

      Object result = cache.get(nodeId);
      if (result != null) {
        visualisationCallback.accept(
            new ExpressionValue(
                nodeId,
                Types.getName(result).orElse(null),
                result,
                cache.getEnterable(nodeId)));
        throw context.createUnwind(result);
      }
    }

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
        UUID nodeId = ((FunctionCallInstrumentationNode) node).getId();
        cache.putEnterable(nodeId, (FunctionCallInstrumentationNode.FunctionCall) result);
      } else if (node instanceof ExpressionNode) {
        UUID nodeId = ((ExpressionNode) node).getId();
        cache.offer(nodeId, result);
        valueCallback.accept(
            new ExpressionValue(
                nodeId, Types.getName(result).orElse(null), result, cache.getEnterable(nodeId)));
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

  /**
   * Attach a new listener to observe identified nodes within given function.
   *
   * @param entryCallTarget the call target being observed.
   * @param funSourceStart the source start of the observed range of ids.
   * @param funSourceLength the length of the observed source range.
   * @param cache the precomputed expression values.
   * @param valueCallback the consumer of the node value events.
   * @param visualisationCallback the consumer of the node visualisation events.
   * @return a reference to the attached event listener.
   */
  public EventBinding<ExecutionEventListener> bind(
      CallTarget entryCallTarget,
      int funSourceStart,
      int funSourceLength,
      RuntimeCache cache,
      Consumer<ExpressionValue> valueCallback,
      Consumer<IdExecutionInstrument.ExpressionValue> visualisationCallback) {
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
                new IdExecutionEventListener(
                    entryCallTarget,
                    cache,
                    valueCallback,
                    visualisationCallback));
    return binding;
  }
}
