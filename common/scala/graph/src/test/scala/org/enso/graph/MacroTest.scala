package org.enso.graph

import org.enso.graph.Graph.Node
import org.enso.graph.definition.Macro.field
import org.scalatest.{FlatSpec, Matchers}

class MacroTest extends FlatSpec with Matchers {

  "The `@field` macro" should "generate sensible code" in {
    @field case class Shape[G <: Graph](source: Node[G], target: Node[G])

  }

  // TODO [AA] More tests for the macro functionality.
  // TODO [AA] Should include `shouldCompile` and `shouldNotCompile` tests for
  //  the error cases.


}
