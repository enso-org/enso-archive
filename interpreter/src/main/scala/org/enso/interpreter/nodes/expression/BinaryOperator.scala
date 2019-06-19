package org.enso.interpreter.nodes.expression

import com.oracle.truffle.api.dsl.NodeChild
import org.enso.interpreter.nodes.expression.Test.nodeChild

import scala.annotation.meta.field

object Test {
  type nodeChild = NodeChild @field
}

@nodeChild("leftOperand")
@nodeChild("rightOperand")
abstract class BinaryOperator {}
