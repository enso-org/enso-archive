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

public class GetStateNode extends RootNode {
  private GetStateNode(Language language) {
    super(language);
  }

  @Override
  public Object execute(VirtualFrame frame) {
    Object state = Function.ArgumentsHelper.getState(frame.getArguments());
    return new Stateful(state, state);
  }

  public static Function makeFunction(Language language) {
    return Function.fromRootNode(
        new GetStateNode(language),
        new ArgumentDefinition(0, "this", ArgumentDefinition.ExecutionMode.EXECUTE));
  }
}
