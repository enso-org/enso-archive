package org.enso.interpreter.node;

import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.SourceSection;
import org.enso.interpreter.Language;
import org.enso.interpreter.runtime.callable.function.Function;
import org.enso.interpreter.runtime.state.Stateful;

/**
 * This node represents the root of all Enso computations.
 *
 * <p>All new computations in Enso must be executed from within an {@link EnsoRootNode}, as
 * determined by the API provided by Truffle.
 */
public class EnsoRootNode extends RootNode {
  private final String name;
  private final SourceSection sourceSection;
  @Child private ExpressionNode body;
  private final FrameSlot stateFrameSlot;
  private final boolean deStatify;

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
      String name,
      boolean deStatify) {
    super(language, frameDescriptor);
    this.body = body;
    this.sourceSection = section;
    this.name = name;
    this.stateFrameSlot = frameDescriptor.findOrAddFrameSlot("<<state>>", FrameSlotKind.Object);
    this.deStatify = deStatify;
  }

  public EnsoRootNode(
      Language language,
      FrameDescriptor frameDescriptor,
      ExpressionNode body,
      SourceSection section,
      String name) {
    this(language, frameDescriptor, body, section, name, false);
  }

  /**
   * Executes the node.
   *
   * @param frame the stack frame to execute in
   * @return the result of executing this node
   */
  @Override
  public Object execute(VirtualFrame frame) {
    if (frame.getArguments().length == 0) {
      frame.setObject(stateFrameSlot, null);
    } else {
      frame.setObject(stateFrameSlot, Function.ArgumentsHelper.getState(frame.getArguments()));
    }
    Object result = body.executeGeneric(frame);
    Object state = FrameUtil.getObjectSafe(frame, stateFrameSlot);
    if (deStatify) {
      return result;
    } else {
      return new Stateful(state, result);
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

  public FrameSlot getStateFrameSlot() {
    return stateFrameSlot;
  }
}
