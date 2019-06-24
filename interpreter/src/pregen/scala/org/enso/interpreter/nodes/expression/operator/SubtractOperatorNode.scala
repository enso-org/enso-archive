package org.enso.interpreter.nodes.expression.operator

import com.oracle.truffle.api.dsl.Specialization
import com.oracle.truffle.api.nodes.NodeInfo
import org.enso.interpreter.nodes.expression.BinaryOperatorNode

@NodeInfo(shortName = "-")
abstract class SubtractOperatorNode extends BinaryOperatorNode {

  @Specialization
  protected def subtract(left: Long, right: Long): Long =
    left - right
}
