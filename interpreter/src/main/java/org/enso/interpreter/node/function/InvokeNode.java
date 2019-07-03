package org.enso.interpreter.node.function;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.NodeInfo;
import org.enso.interpreter.TailCallException;
import org.enso.interpreter.TypeError;
import org.enso.interpreter.node.ExpressionNode;
import org.enso.interpreter.runtime.Function;

@NodeInfo(shortName = "@", description = "Executes block from children expression")
public final class InvokeNode extends ExpressionNode {
  @Child private ExpressionNode expression;
  @Children private final ExpressionNode[] arguments;
  @Child private InteropLibrary library;

  public InvokeNode(ExpressionNode expression, ExpressionNode[] arguments) {
    this.expression = expression;
    this.arguments = arguments;
    library = InteropLibrary.getFactory().createDispatched(3);
  }

  @Override
  @ExplodeLoop
  public Object executeGeneric(VirtualFrame frame) {
    Function function = (Function) expression.executeGeneric(frame);
    Object[] positionalArguments = new Object[arguments.length];
//    positionalArguments[0] = function.getScope();
    for (int i = 0; i < arguments.length; i++) {
      positionalArguments[i] = arguments[i].executeGeneric(frame);
    }

    CompilerAsserts.compilationConstant(this.isTail());
    if (this.isTail()) {
      throw new TailCallException(function, positionalArguments);
    } else {
      return doCall(function, positionalArguments);
    }
  }

  private Object doCall(Function function, Object[] args) {
    while (true) {
      try {
        return library.execute(function, args);
      } catch (TailCallException e) {
        function = e.getFunction();
        args = e.getArguments();
      } catch (UnsupportedTypeException | ArityException | UnsupportedMessageException e) {
        throw new TypeError("foo", this);
      }
    }
  }
}
