package org.enso.interpreter.node.expression;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.NodeInfo;
import org.enso.interpreter.node.ExpressionNode;
import org.enso.interpreter.node.StatementNode;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;

@NodeInfo(shortName = "ForeignCall")
public class ForeignCallNode extends StatementNode {
  private final String foreignSource;
  private final String language;
  @Children private final ExpressionNode[] args;

  public ForeignCallNode(String language, String foreignSource, ExpressionNode[] args) {
    this.language = language;
    this.foreignSource = foreignSource;
    this.args = args;
  }

  // TODO [AA] Make this do something other than numbers.
  @Override
  public void execute(VirtualFrame frame) {
    Context context = Context.create(this.language);
    Value val = context.eval(this.language, this.foreignSource);
    Object[] arguments = new Object[args.length];
    for (int i=0 ; i < args.length; i++) {
      arguments[i] = args[i].executeGeneric(frame);
    }
    val.execute(arguments);
  }
}
