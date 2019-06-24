package org.enso.interpreter.nodes.expression.operator

import com.oracle.truffle.api.dsl.Specialization
import com.oracle.truffle.api.nodes.NodeInfo
import org.enso.interpreter.nodes.expression.BinaryOperatorNode

@NodeInfo(shortName = "+") abstract class AddOperatorNode
    extends BinaryOperatorNode {

  @Specialization protected def add(left: Long, right: Long): Long =
    Math.addExact(left, right)
}
