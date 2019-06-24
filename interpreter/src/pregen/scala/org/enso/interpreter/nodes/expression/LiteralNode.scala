package org.enso.interpreter.nodes.expression

import com.oracle.truffle.api.nodes.NodeInfo
import org.enso.interpreter.nodes.ExpressionNode

@NodeInfo(
  shortName   = "Literal",
  description = "A node for representing all kinds of literal values."
)
abstract class LiteralNode extends ExpressionNode {}
