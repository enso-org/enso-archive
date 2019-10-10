package org.enso.interpreter.node;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.SourceSection;

/**
 * This node handles static transformation of the input AST before execution.
 *
 * <p>As much of the static transformation and analysis functionality required by the interpreter
 * must have access to the interpreter, it must take place as part of the interpreter context. As a
 * result, this node handles the transformations and re-writes
 */
public class RewriteRootNode extends RootNode {
  private final String name;
  private final SourceSection sourceSection;
  private final boolean isExecutingInStaticContext; // Note [Execution in a Static Context]
  @Child private ExpressionNode ensoProgram = null;
  // TODO [ARA] Storage for the AST

  /* Note [Execution in a Static Context]
   * ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
   */

  public RewriteRootNode(
      TruffleLanguage<?> language,
      FrameDescriptor frameDescriptor,
      String name,
      SourceSection sourceSection,
      boolean isExecutingInStaticContext) {
    super(language, frameDescriptor);
    this.name = name;
    this.sourceSection = sourceSection;
    this.isExecutingInStaticContext = isExecutingInStaticContext;
  }

  /**
   * Executes the static analysis passes before executing the resultant program.
   *
   * @param frame the stack frame to execute in
   * @return the result of executing this node
   */
  @Override
  public Object execute(VirtualFrame frame) {
    // Note [Static Passes]
    if (this.ensoProgram == null) {
      // Note [Static Passes (Lack of Profiling)]
      CompilerDirectives.transferToInterpreterAndInvalidate();
      // TODO [ARA] Begin the desugaring, rewrite, etc, process.
      throw new RuntimeException("No static analysis yet implemented");
    }

    return this.ensoProgram.executeGeneric(frame);
  }

  /* Note [Static Passes]
   * ~~~~~~~~~~~~~~~~~~~~
   * Almost all of the static analysis functionality required by the interpreter requires access to
   * the interpreter to execute small amounts of code. This is for purposes such as:
   * - Type-level computation and evaluation during typechecking.
   * - CTFE for optimisation.
   * - Various other re-write mechanisms that involve code execution.
   *
   * The contract expected from a Truffle Language states that there is to be no access to the
   * interpreter context during parsing, which is the most natural time to perform these
   * transformation passes. As a result, we have to perform them inside the interpreter once parsing
   * is completed.
   *
   * To that end, we have a special kind of root node. It is constructed with the input AST only,
   * and when executed acts as follows:
   * 1. It takes the input AST and executes a sequence of analyses and transformations such that the
   *    end result is a `Node`-based AST representing the program.
   * 2. It rewrites itself to contain the program, and then executes that program.
   *
   * Note [Static Passes (Lack of Profiling)]
   * ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
   * While it is, in general, good practice to profile branches that don't depend on compilation
   * final values in a truffle interpreter, this `if` is only ever executed once. This means that
   * there is no need to profile it as the knowledge can't be used by the partial evaluator in any
   * case.
   */

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
    // TODO [ARA] How do we handle setting this? Will need to use a flag that is set and then
    // acted upon at the end of transformation.
    ensoProgram.markTail();
  }

  /** Marks the node as not tail-recursive. */
  public void markNotTail() {
    this.markNotTail();
  }

  /**
   * Sets whether the node is tail-recursive.
   *
   * @param isTail whether or not the node is tail-recursive.
   */
  public void setTail(boolean isTail) {
    ensoProgram.setTail(isTail);
  }
}
