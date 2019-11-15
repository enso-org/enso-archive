package org.enso.graph

import org.enso.graph.definition.Macro.component
import org.scalatest.{FlatSpec, Matchers}

class ComponentMacroTest extends FlatSpec with Matchers {

  "The `@component` macro" should "define correct components" in {
    @component case class Nodes() { type Node[G <: Graph] }
    @component case class Edges() { type Edge[G <: Graph] }
  }

  // TODO [AA] More tests for the macro functionality:
  //  - Not applied to case class or object
  //  - Generates sensible code for variants
  //  - Object format not correct (subclasses via reflection)

  // TODO [AA] Should include `shouldCompile` and `shouldNotCompile` tests for
  //  the error cases.

}
