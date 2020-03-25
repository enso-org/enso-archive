package org.enso.interpreter.test;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.*;
import com.oracle.truffle.api.nodes.Node;
import org.enso.interpreter.node.ExpressionNode;

import java.util.UUID;

/**
 * A debug instrument used to test code locations.
 *
 * <p>Allows to listen for a node with a given type at a given position, and later verify if such a
 * node was indeed encountered in the course of execution.
 */
@TruffleInstrument.Registration(
    id = CodeIdsTestInstrument.INSTRUMENT_ID,
    services = CodeIdsTestInstrument.class)
public class CodeIdsTestInstrument extends TruffleInstrument {
  public static final String INSTRUMENT_ID = "ids-test";
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

  /**
   * An event listener implementing the behavior of verifying whether the currently executed node is
   * the one expected by the user.
   */
  public static class IdEventListener implements ExecutionEventListener {
    private boolean successful = false;
    private final UUID expectedId;
    private final Object expectedResult;

    public IdEventListener(UUID expectedId, Object expectedResult) {
      this.expectedId = expectedId;
      this.expectedResult = expectedResult;
    }

    public UUID getId() {
      return expectedId;
    }

    /**
     * Was a node with parameters specified for this listener encountered in the course of
     * execution?
     *
     * @return {@code true} if the requested node was observed, {@code false} otherwise
     */
    public boolean isSuccessful() {
      return successful;
    }

    /**
     * Checks if the node to be executed is the node this listener was created to observe.
     *
     * @param context current execution context
     * @param frame current execution frame
     */
    @Override
    public void onEnter(EventContext context, VirtualFrame frame) {}

    @Override
    public void onReturnValue(EventContext context, VirtualFrame frame, Object result) {
      if (successful) {
        return;
      }
      Node node = context.getInstrumentedNode();
      if (!(node instanceof ExpressionNode)) {
        return;
      }
      UUID id = ((ExpressionNode) node).getId();
      if (id != expectedId) {
        return;
      }
      if (expectedResult.equals(result)) {
        successful = true;
      }
    }

    @Override
    public void onReturnExceptional(
        EventContext context, VirtualFrame frame, Throwable exception) {}
  }

  /**
   * Attach a new listener to observe nodes with given parameters.
   *
   * @param length the source length of the expected node
   * @param type the type of the expected node
   * @return a reference to attached event listener
   */
  public EventBinding<IdEventListener> bindTo(UUID id, Object expectedResult) {
    return env.getInstrumenter()
        .attachExecutionEventListener(
            SourceSectionFilter.newBuilder().tagIs(ExpressionNode.IdentifiedTag.class).build(),
            new IdEventListener(id, expectedResult));
  }
}
