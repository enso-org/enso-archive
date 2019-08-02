package org.enso.interpreter.node;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.NodeInfo;
import com.oracle.truffle.api.nodes.UnexpectedResultException;
import org.enso.interpreter.runtime.TypesGen;
import org.enso.interpreter.runtime.function.Function;
import org.enso.interpreter.runtime.type.Atom;
import org.enso.interpreter.runtime.type.AtomConstructor;

@NodeInfo(shortName = "EnsoExpression", description = "The base node for all enso expressions.")
public abstract class ExpressionNode extends BaseNode {

  public long executeLong(VirtualFrame frame) throws UnexpectedResultException {
    return TypesGen.expectLong(executeGeneric(frame));
  }

  public AtomConstructor executeAtomConstructor(VirtualFrame frame)
      throws UnexpectedResultException {
    return TypesGen.expectAtomConstructor(executeGeneric(frame));
  }

  public Atom executeAtom(VirtualFrame frame) throws UnexpectedResultException {
    return TypesGen.expectAtom(executeGeneric(frame));
  }

  public Function executeFunction(VirtualFrame frame) throws UnexpectedResultException {
    return TypesGen.expectFunction(executeGeneric(frame));
  }

  public abstract Object executeGeneric(VirtualFrame frame);

  public void executeVoid(VirtualFrame frame) {
    executeGeneric(frame);
  }
}
