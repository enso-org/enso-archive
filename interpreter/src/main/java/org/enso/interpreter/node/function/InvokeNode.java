package org.enso.interpreter.node.function;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import com.oracle.truffle.api.nodes.NodeInfo;
import org.enso.interpreter.TailCallException;
import org.enso.interpreter.TypeError;
import org.enso.interpreter.node.ExpressionNode;
import org.enso.interpreter.runtime.Block;
import scala.reflect.api.Exprs;

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
    Block block = (Block) expression.executeGeneric(frame);
    Object[] positionalArguments = new Object[arguments.length];
//    positionalArguments[0] = block.getScope();
    for (int i = 0; i < arguments.length; i++) {
      positionalArguments[i] = arguments[i].executeGeneric(frame);
    }

    CompilerAsserts.compilationConstant(this.isTail());
    if (this.isTail()) {
      throw new TailCallException(block, positionalArguments);
    } else {
      return doCall(block, positionalArguments);
    }
  }

  private Object doCall(Block block, Object[] args) {
    while (true) {
      try {
        return library.execute(block, args);
      } catch (TailCallException e) {
        block = e.getBlock();
        args = e.getArguments();
      } catch (UnsupportedTypeException | ArityException | UnsupportedMessageException e) {
        throw new TypeError("foo", this);
      }
    }
  }
}
