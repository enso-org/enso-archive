package org.enso.interpreter.node.controlflow;

import com.oracle.truffle.api.frame.VirtualFrame;
import org.enso.interpreter.runtime.callable.atom.Atom;
import org.enso.interpreter.runtime.error.InexhaustivePatternMatchException;

public class DefaultFallbackNode extends CaseNode {
  @Override
  public void execute(VirtualFrame frame, Atom target) {
    throw new InexhaustivePatternMatchException(this.getParent());
  }
}
