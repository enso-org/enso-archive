package org.enso.interpreter.nodes.expression

import com.oracle.truffle.api.frame.VirtualFrame
import com.oracle.truffle.api.nodes.Node.Child
import com.oracle.truffle.api.nodes.NodeInfo
import org.enso.interpreter.nodes.ExpressionNode

@NodeInfo(
  shortName   = "Parentheses",
  description = "A representation of parenthesised expressions."
)
class ParenthesesNode(@Child expression: ExpressionNode)
    extends ExpressionNode {

  override def execute(frame: VirtualFrame): Any =
    expression.execute(frame)
}
