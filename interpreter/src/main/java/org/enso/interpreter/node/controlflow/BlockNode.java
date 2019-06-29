package org.enso.interpreter.node.controlflow;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.NodeInfo;
import com.oracle.truffle.api.nodes.RootNode;
import org.enso.interpreter.Language;
import org.enso.interpreter.node.ExpressionNode;
import org.enso.interpreter.node.StatementNode;

@NodeInfo(shortName = "{}.exec", description = "Suspended computation")
public class BlockNode extends RootNode {

  @Children private final StatementNode[] statements;
  @Child private ExpressionNode returnExpr;

  public BlockNode(Language language, FrameDescriptor frameDescriptor, StatementNode[] statements, ExpressionNode returnExpr) {
    super(language, frameDescriptor);
    this.statements = statements;
    this.returnExpr = returnExpr;
  }

  @Override
  @ExplodeLoop
  public Object execute(VirtualFrame frame) {
    for (StatementNode statement : statements) {
      statement.execute(frame);
    }
    return returnExpr.executeGeneric(frame);
  }
}
