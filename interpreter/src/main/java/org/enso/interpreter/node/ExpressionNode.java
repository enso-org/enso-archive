package org.enso.interpreter.node;

import com.oracle.truffle.api.dsl.ReportPolymorphism;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeInfo;
import org.enso.interpreter.node.util.SourceLoc;

@NodeInfo(shortName = "EnsoExpression", description = "The base node for all enso expressions.")
// @GenerateWrapper TODO [AA] Fix this.
@ReportPolymorphism
public abstract class ExpressionNode extends StatementNode {
  public abstract Object executeGeneric(VirtualFrame frame);

  public void execute(VirtualFrame frame) {
    executeGeneric(frame);
  }
}
