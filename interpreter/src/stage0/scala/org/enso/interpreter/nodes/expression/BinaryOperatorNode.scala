package org.enso.interpreter.nodes.expression

import com.oracle.truffle.api.dsl.NodeChild
import com.oracle.truffle.api.nodes.NodeInfo
import org.enso.interpreter.nodes.ExpressionNode

@NodeInfo(
  shortName   = "BinaryOperator",
  description = "A base for all binary operators in Enso."
)
@NodeChild("leftOperand")
@NodeChild("rightOperand") abstract class BinaryOperatorNode
    extends ExpressionNode {}
