package org.enso.graph

import org.enso.graph.GraphComponents.Node
import org.enso.graph.definition.Macro.field
import org.scalatest.{FlatSpec, Matchers}

class MacroTest extends FlatSpec with Matchers {

  "The `@field` macro" should "generate sensible code" in {
    @field case class Shape[G <: Graph](source: Node[G], target: Node[G])
  }

  // TODO [AA] More tests for the macro functionality:
  //  - Not applied to case class or object
  //  - Generates sensible code for variants
  //  - Object format not correct (subclasses via reflection)

  // TODO [AA] Should include `shouldCompile` and `shouldNotCompile` tests for
  //  the error cases.

}
