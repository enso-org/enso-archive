package org.enso.interpreter.node.expression;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.NodeInfo;
import org.enso.interpreter.node.ExpressionNode;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;

@NodeInfo(shortName = "ForeignCall")
public class ForeignCallNode extends ExpressionNode {
  private final String foreignSource;
  private final String language;

  public ForeignCallNode(String language, String foreignSource) {
    this.language = language;
    this.foreignSource = foreignSource;
  }

  // TODO [AA] Make this do something other than numbers.
  @Override
  public Object execute(VirtualFrame frame) {
    Context context = Context.create(this.language);

    Value val = context.eval(this.language, this.foreignSource);
    return val.asLong();
  }
}
