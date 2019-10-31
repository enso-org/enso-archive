package org.enso.interpreter.node.expression.builtin.state;

import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.RootNode;
import org.enso.interpreter.Language;
import org.enso.interpreter.runtime.callable.argument.ArgumentDefinition;
import org.enso.interpreter.runtime.callable.function.ArgumentSchema;
import org.enso.interpreter.runtime.callable.function.Function;
import org.enso.interpreter.runtime.state.Stateful;

public class PutStateNode extends RootNode {
  private PutStateNode(Language language) {
    super(language);
  }

  @Override
  public Object execute(VirtualFrame frame) {
    Object newState = Function.ArgumentsHelper.getPositionalArguments(frame.getArguments())[1];
    return new Stateful(newState, newState);
  }

  public static Function makeFunction(Language language) {
    return Function.fromRootNode(
        new PutStateNode(language),
        new ArgumentDefinition(0, "this", ArgumentDefinition.ExecutionMode.EXECUTE),
        new ArgumentDefinition(1, "newState", ArgumentDefinition.ExecutionMode.EXECUTE));
  }
}
