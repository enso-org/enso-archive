package org.enso.interpreter.node.function.argument;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.NodeInfo;
import com.oracle.truffle.api.profiles.ConditionProfile;
import org.enso.interpreter.node.ExpressionNode;
import org.enso.interpreter.runtime.function.Function;

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
    Object argument = Function.ArgumentsHelper.getPositionalArguments(frame.getArguments())[index];

    if (defaultValue == null) {
      return argument;
    }

    if (defaultingProfile.profile(argument instanceof DefaultedArgumentNode)) {
      return defaultValue.executeGeneric(frame);
    } else {
      return argument;
    }
  }
}
