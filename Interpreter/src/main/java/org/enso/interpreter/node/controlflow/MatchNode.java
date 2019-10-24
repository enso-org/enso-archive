package org.enso.interpreter.node.controlflow;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.UnexpectedResultException;
import com.oracle.truffle.api.profiles.BranchProfile;
import org.enso.interpreter.node.ExpressionNode;
import org.enso.interpreter.runtime.callable.atom.Atom;
import org.enso.interpreter.runtime.callable.function.Function;
import org.enso.interpreter.runtime.error.TypeError;

import java.util.function.Consumer;

/** A node representing a pattern match on an Atom. */
@NodeChild(value = "target", type = ExpressionNode.class)
public abstract class MatchNode extends ExpressionNode {
  @Children private final CaseNode[] cases;
  @Child private CaseNode fallback;
  private final BranchProfile typeErrorProfile = BranchProfile.create();

  protected MatchNode(CaseNode[] cases, CaseNode fallback) {
    this.cases = cases;
    this.fallback = fallback;
  }

  /**
   * Sets whether or not the pattern match is tail-recursive.
   *
   * @param isTail whether or not the expression is tail-recursive
   */
  @Override
  @ExplodeLoop
  public void setTail(boolean isTail) {
    for (CaseNode caseNode : cases) {
      caseNode.setTail(isTail);
    }
    fallback.setTail(isTail);
  }

  @ExplodeLoop
  @Specialization
  protected Object doAtom(VirtualFrame frame, Atom atom) {
    try {
      for (CaseNode caseNode : cases) {
        caseNode.executeAtom(frame, atom);
      }
      fallback.executeAtom(frame, atom);
      CompilerDirectives.transferToInterpreter();
      throw new RuntimeException("Impossible behavior.");

    } catch (BranchSelectedException e) {
      // Note [Branch Selection Control Flow]
      return e.getResult();

    } catch (UnexpectedResultException e) {
      typeErrorProfile.enter();
      throw new TypeError("Expected an Atom.", this);
    }
  }

  @ExplodeLoop
  @Specialization
  protected Object doFun(VirtualFrame frame, Function function) {
    try {
      for (CaseNode caseNode : cases) {
        caseNode.executeFunction(frame, function);
      }
      fallback.executeFunction(frame, function);
      CompilerDirectives.transferToInterpreter();
      throw new RuntimeException("Impossible behavior.");

    } catch (BranchSelectedException e) {
      // Note [Branch Selection Control Flow]
      return e.getResult();

    } catch (UnexpectedResultException e) {
      typeErrorProfile.enter();
      throw new TypeError("Expected an Atom.", this);
    }
  }

  @ExplodeLoop
  @Specialization
  protected Object doNumber(VirtualFrame frame, long number) {
    try {

      for (CaseNode caseNode : cases) {
        caseNode.executeNumber(frame, number);
      }
      fallback.executeNumber(frame, number);
      CompilerDirectives.transferToInterpreter();
      throw new RuntimeException("Impossible behavior.");

    } catch (BranchSelectedException e) {
      // Note [Branch Selection Control Flow]
      return e.getResult();

    } catch (UnexpectedResultException e) {
      typeErrorProfile.enter();
      throw new TypeError("Expected an Atom.", this);
    }
  }

  /* Note [Branch Selection Control Flow]
   * ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
   * Truffle provides no easy way to return control flow from multiple paths. This is entirely due
   * to Java's (current) lack of support for case-expressions.
   *
   * As a result, this implementation resorts to using an exception to short-circuit evaluation on
   * a successful match. This short-circuiting also allows us to hand the result of evaluation back
   * out to the caller (here) wrapped in the exception.
   *
   * The main alternative to this was desugaring to a nested-if, which would've been significantly
   * harder to maintain, and also resulted in significantly higher code complexity.
   */
}
