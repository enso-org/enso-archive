package org.enso.interpreter.node.function;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.nodes.*;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.profiles.LoopConditionProfile;
import org.enso.interpreter.TailCallException;
import org.enso.interpreter.TypeError;
import org.enso.interpreter.node.ExpressionNode;
import org.enso.interpreter.runtime.Function;

@NodeInfo(shortName = "@", description = "Executes block from child expression")
public final class InvokeNode extends ExpressionNode {
  @Child private ExpressionNode expression;
  @Children private final ExpressionNode[] arguments;
  @Child private CallNode callNode;

  public InvokeNode(ExpressionNode expression, ExpressionNode[] arguments) {
    this.expression = expression;
    this.arguments = arguments;
    this.callNode = new SimpleCallNode();
  }

  @Override
  @ExplodeLoop
  public Object executeGeneric(VirtualFrame frame) {
    Function function = (Function) expression.executeGeneric(frame);
    Object[] positionalArguments = new Object[arguments.length];
    for (int i = 0; i < arguments.length; i++) {
      positionalArguments[i] = arguments[i].executeGeneric(frame);
    }

    CompilerAsserts.compilationConstant(this.isTail());
    if (this.isTail()) {
      throw new TailCallException(function, positionalArguments);
    } else {
      return callNode.doCall(frame, function, positionalArguments);
    }
  }
}
