package org.enso.interpreter.nodes.expression.literal

import com.oracle.truffle.api.frame.VirtualFrame
import com.oracle.truffle.api.nodes.NodeInfo
import org.enso.interpreter.nodes.expression.LiteralNode

@NodeInfo(shortName = "IntegerLiteral")
final class IntegerLiteralNode(value: Long) extends LiteralNode {
  override def execute(frame: VirtualFrame): Any = value
}
