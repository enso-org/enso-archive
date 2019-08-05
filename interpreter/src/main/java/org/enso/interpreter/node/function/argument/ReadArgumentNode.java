package org.enso.interpreter.node.function.argument;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.NodeInfo;
import com.oracle.truffle.api.profiles.ConditionProfile;
import org.enso.interpreter.node.ExpressionNode;
import org.enso.interpreter.node.function.argument.sentinel.DefaultedArgumentNode;
import org.enso.interpreter.runtime.function.Function;
import org.enso.interpreter.runtime.function.Function.ArgumentsHelper;

@NodeInfo(description = "Read function argument.")
public class ReadArgumentNode extends ExpressionNode {
  private final int index;
  @Child ExpressionNode defaultValue;
  private final ConditionProfile defaultingProfile = ConditionProfile.createCountingProfile();

  public ReadArgumentNode(int index, ExpressionNode defaultValue) {
    this.index = index;
    this.defaultValue = defaultValue;
  }

  @Override
  public Object executeGeneric(VirtualFrame frame) {
    if (defaultValue == null) {
      return Function.ArgumentsHelper.getPositionalArguments(frame.getArguments())[index];
    }

    Object argument = null;

    if (index < ArgumentsHelper.getPositionalArguments(frame.getArguments()).length) {
      argument = Function.ArgumentsHelper.getPositionalArguments(frame.getArguments())[index];
    }

    if (defaultingProfile.profile(argument instanceof DefaultedArgumentNode || argument == null)) {
      return defaultValue.executeGeneric(frame);
    } else {
      return argument;
    }
  }
}
