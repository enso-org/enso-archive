package org.enso.interpreter.nodes.expression

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary
import com.oracle.truffle.api.dsl.Specialization
import com.oracle.truffle.api.nodes.NodeInfo

@NodeInfo(shortName = "+")
abstract class AddNode extends BinaryOperator {

  @Specialization(rewriteOn = Array(classOf[ArithmeticException]))
  protected def add(left: Long, right: Long): Long = Math.addExact(left, right)

  @Specialization
  @TruffleBoundary
  protected def add(left: BigInt, right: BigInt): BigInt = left + right
}
