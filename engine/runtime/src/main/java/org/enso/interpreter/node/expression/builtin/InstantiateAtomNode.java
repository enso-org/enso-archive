package org.enso.interpreter.node.expression.builtin;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import org.enso.interpreter.Language;
import org.enso.interpreter.node.ExpressionNode;
import org.enso.interpreter.runtime.callable.atom.AtomConstructor;
import org.enso.interpreter.runtime.callable.function.Function;
import org.enso.interpreter.runtime.state.Stateful;

public class InstantiateAtomNode extends RootNode {
  private @Node.Child ExpressionNode instantiator;
  private final String name;

  private InstantiateAtomNode(Language language, String name, ExpressionNode instantiator) {
    super(language);
    this.name = name;
    this.instantiator = instantiator;
  }

  @Override
  public Stateful execute(VirtualFrame frame) {
    return new Stateful(
        Function.ArgumentsHelper.getState(frame.getArguments()),
        instantiator.executeGeneric(frame));
  }

  @Override
  public String getName() {
    return "constructor::" + name;
  }

  public static InstantiateAtomNode build(
      Language language, String name, ExpressionNode instantiator) {
    return new InstantiateAtomNode(language, name, instantiator);
  }
}
