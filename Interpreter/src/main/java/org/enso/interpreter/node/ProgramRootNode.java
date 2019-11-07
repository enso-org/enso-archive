package org.enso.interpreter.node;

import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlotKind;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;
import org.enso.interpreter.Language;
import org.enso.interpreter.runtime.Context;

/**
 * This node handles static transformation of the input AST before execution and represents the root
 * of an Enso program.
 *
 * <p>As much of the static transformation and analysis functionality required by the interpreter
 * must have access to the interpreter, it must take place as part of the interpreter context. As a
 * result, this node handles the transformations and re-writes
 */
public class ProgramRootNode extends EnsoRootNode {

  private final Source sourceCode;
  @Child private ExpressionNode ensoProgram = null;
  private boolean programShouldBeTailRecursive = false;

  /**
   * Constructs the root node.
   *
   * @param language the language instance in which this will execute
   * @param frameDescriptor a reference to the program root frame
   * @param name the name of the program
   * @param sourceSection a reference to the source code being executed
   * @param sourceCode the code to compile and execute
   */
  public ProgramRootNode(
      Language language,
      FrameDescriptor frameDescriptor,
      String name,
      SourceSection sourceSection,
      Source sourceCode) {
    super(
        language,
        frameDescriptor,
        name,
        sourceSection,
        frameDescriptor.findOrAddFrameSlot("<<state>>", FrameSlotKind.Object));
    this.sourceCode = sourceCode;
  }

  /**
   * Executes the static analysis passes before executing the resultant program.
   *
   * @param frame the stack frame to execute in
   * @return the result of executing this node
   */
  @Override
  public Object execute(VirtualFrame frame) {
    Context context = this.getLanguage().getCurrentContext();

    // Note [Static Passes]
    if (this.ensoProgram == null) {
      this.ensoProgram = this.insert(context.compiler().run(this.sourceCode));
    }

    this.ensoProgram.setTail(programShouldBeTailRecursive);
    Object state = getContext().getUnit().newInstance();
    frame.setObject(this.getStateFrameSlot(), state);

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
   * 1. It takes the input source and executes a sequence of analyses and transformations such that
   *    the end result is a `Node`-based AST representing the program.
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
   * Sets whether the node is tail-recursive.
   *
   * @param isTail whether or not the node is tail-recursive.
   */
  @Override
  public void setTail(boolean isTail) {
    // Note [Delayed Tail Calls]
    this.programShouldBeTailRecursive = isTail;
  }

  /* Note [Delayed Tail Calls]
   * ~~~~~~~~~~~~~~~~~~~~~~~~~
   * As there is no guarantee that the program has been generated at the point at which setTail is
   * called, we need to ensure that the tail-calledness information still makes its way to the
   * program itself.
   *
   * To do this, we set a variable internally, that is then passed to the program just before it is
   * executed.
   */
}
