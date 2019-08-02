package org.enso.interpreter.node.controlflow;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.UnexpectedResultException;
import org.enso.interpreter.node.BaseNode;
import org.enso.interpreter.runtime.type.Atom;

public abstract class CaseNode extends BaseNode {
  public abstract void execute(VirtualFrame frame, Atom target) throws UnexpectedResultException;
}
