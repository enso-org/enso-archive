package org.enso.interpreter.node.expression.builtin.number;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.NodeInfo;
import com.oracle.truffle.api.profiles.BranchProfile;
import org.enso.interpreter.Language;
import org.enso.interpreter.node.expression.builtin.BuiltinRootNode;
import org.enso.interpreter.runtime.callable.argument.ArgumentDefinition;
import org.enso.interpreter.runtime.callable.function.Function;
import org.enso.interpreter.runtime.callable.function.FunctionSchema.CallStrategy;
import org.enso.interpreter.runtime.error.TypeError;
import org.enso.interpreter.runtime.state.Stateful;
import org.enso.interpreter.runtime.type.TypesGen;

@NodeInfo(shortName = "Number.*", description = "Multiplication on numbers.")
public class MultiplyNode extends BuiltinRootNode {
  private BranchProfile thatOpBadTypeProfile = BranchProfile.create();

  private MultiplyNode(Language language) {
    super(language);
  }

  /**
   * Executes this node.
   *
   * @param frame current execution frame
   * @return the result of multiplying the two operands
   */
  @Override
  public Stateful execute(VirtualFrame frame) {
    long left = (long) Function.ArgumentsHelper.getPositionalArguments(frame.getArguments())[0];

    Object right = Function.ArgumentsHelper.getPositionalArguments(frame.getArguments())[1];

    if (TypesGen.isLong(right)) {
      long longRight = TypesGen.asLong(right);
      Object state = Function.ArgumentsHelper.getState(frame.getArguments());

      return new Stateful(state, left * longRight);
    } else {
      thatOpBadTypeProfile.enter();
      throw new TypeError("Unexpected type for `that` operand in " + getName(), this);
    }
  }

  /**
   * Creates a two-argument function wrapping this node.
   *
   * @param language the current language instance
   * @return a function wrapping this node
   */
  public static Function makeFunction(Language language) {
    return Function.fromBuiltinRootNode(
        new MultiplyNode(language),
        CallStrategy.ALWAYS_DIRECT,
        new ArgumentDefinition(0, "this", ArgumentDefinition.ExecutionMode.EXECUTE),
        new ArgumentDefinition(1, "that", ArgumentDefinition.ExecutionMode.EXECUTE));
  }

  /** Returns a language-specific name for this node.
   *
   * @return the name of this node
   */
  @Override
  public String getName() {
    return "Number.*";
  }
}
