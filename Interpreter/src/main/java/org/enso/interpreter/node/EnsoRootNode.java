package org.enso.interpreter.node;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.dsl.ReportPolymorphism;
import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.SourceSection;
import org.enso.interpreter.Language;
import org.enso.interpreter.runtime.Context;
import org.enso.interpreter.runtime.callable.function.Function;
import org.enso.interpreter.runtime.state.Stateful;

/**
 * This node represents the root of all Enso computations.
 *
 * <p>All new computations in Enso must be executed from within an {@link EnsoRootNode}, as
 * determined by the API provided by Truffle.
 */
@ReportPolymorphism
public class EnsoRootNode extends RootNode {
  private final String name;
  private final SourceSection sourceSection;
  @Child private ExpressionNode body;
  private final FrameSlot stateFrameSlot;
  private final boolean returnFinalState;
  private @CompilerDirectives.CompilationFinal TruffleLanguage.ContextReference<Context>
      contextReference;

  /**
   * Creates a new root node.
   *
   * @param language the language identifier
   * @param frameDescriptor a description of the stack frame
   * @param body the program body to be executed
   * @param section a mapping from {@code body} to the program source
   * @param name a name for the node
   * @param returnFinalState whether this node should return the final state together with the
   *     result
   */
  public EnsoRootNode(
      Language language,
      FrameDescriptor frameDescriptor,
      ExpressionNode body,
      SourceSection section,
      String name,
      boolean returnFinalState) {
    super(language, frameDescriptor);
    this.body = body;
    this.sourceSection = section;
    this.name = name;
    this.stateFrameSlot = frameDescriptor.findOrAddFrameSlot("<<state>>", FrameSlotKind.Object);
    this.returnFinalState = returnFinalState;
  }

  /**
   * Creates a new root node.
   *
   * @param language the language identifier
   * @param frameDescriptor a description of the stack frame
   * @param body the program body to be executed
   * @param section a mapping from {@code body} to the program source
   * @param name a name for the node
   */
  public EnsoRootNode(
      Language language,
      FrameDescriptor frameDescriptor,
      ExpressionNode body,
      SourceSection section,
      String name) {
    this(language, frameDescriptor, body, section, name, true);
  }

  private Context getContext() {
    if (contextReference == null) {
      CompilerDirectives.transferToInterpreterAndInvalidate();
      contextReference = lookupContextReference(Language.class);
    }
    return contextReference.get();
  }

  /**
   * Executes the node.
   *
   * @param frame the stack frame to execute in
   * @return the result of executing this node
   */
  @Override
  public Object execute(VirtualFrame frame) {
    Object state =
        frame.getArguments().length == 0
            ? getContext().getUnit().newInstance()
            : Function.ArgumentsHelper.getState(frame.getArguments());
    frame.setObject(stateFrameSlot, state);
    Object result = body.executeGeneric(frame);
    state = FrameUtil.getObjectSafe(frame, stateFrameSlot);
    if (returnFinalState) {
      return new Stateful(state, result);
    } else {
      return result;
    }
  }

  /**
   * Converts this node to a textual representation good for debugging.
   *
   * @return a {@link String} representation of this node
   */
  @Override
  public String toString() {
    return this.name;
  }

  /** Marks the node as tail-recursive. */
  public void markTail() {
    body.markTail();
  }

  /** Marks the node as not tail-recursive. */
  public void markNotTail() {
    body.markNotTail();
  }

  /**
   * Sets whether the node is tail-recursive.
   *
   * @param isTail whether or not the node is tail-recursive.
   */
  public void setTail(boolean isTail) {
    body.setTail(isTail);
  }

  /**
   * Returns the frame slot reference to state variable.
   *
   * @return the frame slot corresponding to state monad
   */
  FrameSlot getStateFrameSlot() {
    return stateFrameSlot;
  }
}
