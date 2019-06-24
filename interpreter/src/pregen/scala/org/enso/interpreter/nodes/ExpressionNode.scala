package org.enso.interpreter.nodes

import com.oracle.truffle.api.dsl.ReportPolymorphism
import com.oracle.truffle.api.frame.VirtualFrame
import com.oracle.truffle.api.nodes.Node
import com.oracle.truffle.api.nodes.NodeInfo
import org.enso.interpreter.nodes.util.SourceLoc

@NodeInfo(
  shortName   = "EnsoExpression",
  description = "The base node for all enso expressions."
)
//@GenerateWrapper TODO [AA] Fix this
@ReportPolymorphism abstract class ExpressionNode extends Node {
  // TODO [AA] Base of the node hierarchy
  private val sourceLocation = SourceLoc.empty

  def execute(frame: VirtualFrame): Any
}
