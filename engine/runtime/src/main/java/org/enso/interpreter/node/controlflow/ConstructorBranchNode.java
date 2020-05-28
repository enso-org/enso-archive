package org.enso.interpreter.node.controlflow;

import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.FrameUtil;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.NodeInfo;
import com.oracle.truffle.api.nodes.UnexpectedResultException;
import com.oracle.truffle.api.profiles.ConditionProfile;
import org.enso.interpreter.node.ExpressionNode;
import org.enso.interpreter.node.callable.ExecuteCallNode;
import org.enso.interpreter.node.callable.ExecuteCallNodeGen;
import org.enso.interpreter.runtime.callable.atom.Atom;
import org.enso.interpreter.runtime.callable.atom.AtomConstructor;
import org.enso.interpreter.runtime.callable.function.Function;
import org.enso.interpreter.runtime.type.TypesGen;

/** An implementation of the case expression specialised to working on constructors. */
@NodeInfo(shortName = "ConsCaseNode")
public abstract class ConstructorBranchNode extends BranchNode {
  @Child private ExpressionNode matcher;
  @Child private ExpressionNode branch;
  @Child private ExecuteCallNode executeCallNode = ExecuteCallNodeGen.create();
  private final ConditionProfile profile = ConditionProfile.createCountingProfile();
  private final ConditionProfile atomTypeProfile = ConditionProfile.createCountingProfile();

  ConstructorBranchNode(ExpressionNode matcher, ExpressionNode branch) {
    this.matcher = matcher;
    this.branch = branch;
  }

  /**
   * Creates a new node for handling matching on a case expression.
   *
   * @param matcher the expression to use for matching
   * @param branch the expression to be executed if (@code matcher} matches
   * @return a node for matching in a case expression
   */
  public static ConstructorBranchNode build(ExpressionNode matcher, ExpressionNode branch) {
    return ConstructorBranchNodeGen.create(matcher, branch);
  }

  /**
   * Handles the atom scrutinee case.
   *
   * <p>The atom's constructor is checked and if it matches the conditional branch is executed with
   * all the atom's fields as arguments.
   *
   * @param frame the stack frame in which to execute
   * @param target the atom to destructure
   * @throws UnexpectedResultException when evaluation fails
   */
  @Specialization
  public Object doAtom(VirtualFrame frame, Atom target) {
    Object matcherVal = matcher.executeGeneric(frame);
    AtomConstructor constructor;

    if (atomTypeProfile.profile(TypesGen.isAtom(matcherVal))) {
      constructor = TypesGen.asAtom(matcherVal).getConstructor();
    } else {
      constructor = TypesGen.asAtomConstructor(matcherVal);
    }

    Object state = FrameUtil.getObjectSafe(frame, getStateFrameSlot());
    if (profile.profile(constructor == target.getConstructor())) {
      Function function = TypesGen.asFunction(branch.executeGeneric(frame));

      // Note [Caller Info For Case Branches]
      throw new BranchSelectedException(
          executeCallNode.executeCall(
              function, null, state, target.getFields()));
    }

    return null;
  }

  @Fallback
  public Object doFallback(VirtualFrame frame, Object target) {
    return null;
  }

  /* Note [Caller Info For Case Branches]
   * ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
   * It is assumed that functions serving as pattern match logic branches are always function
   * literals, not references, curried functions etc. Therefore, as function literals, they
   * have no way of accessing the caller frame and can safely be passed null.
   */
}
