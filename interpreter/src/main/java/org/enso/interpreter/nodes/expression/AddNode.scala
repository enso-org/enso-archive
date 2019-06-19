package org.enso.interpreter.nodes.expression

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary
import com.oracle.truffle.api.dsl.NodeChild
import com.oracle.truffle.api.dsl.Specialization
import com.oracle.truffle.api.nodes.NodeInfo

@NodeChild("foo")
@NodeChild("bar")
@NodeInfo(shortName = "+")
abstract class AddNode extends BinaryOperatorTwo {

  @Specialization(rewriteOn = Array(classOf[ArithmeticException]))
  def add(left: Long, right: Long): Long = Math.addExact(left, right)

  @Specialization
  @TruffleBoundary
  def add(left: BigInt, right: BigInt): BigInt = left + right
}
