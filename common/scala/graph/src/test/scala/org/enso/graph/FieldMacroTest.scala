package org.enso.graph

import org.enso.graph.GraphComponents.{Edge, Node}
import org.enso.graph.definition.Macro.field
import org.scalatest.{FlatSpec, Matchers}

class FieldMacroTest extends FlatSpec with Matchers {

  "The `@field` macro" should "work for single fields" in {
    @field case class Shape[G <: Graph](source: Node[G], target: Node[G])
  }

  "The `@field` macro" should "work for variant fields" in {
    @field object Shape {
      case class Null()
      case class App[G <: Graph](fn: Edge[G], argTest: Edge[G])
    }
  }

  // TODO [AA] More tests for the macro functionality:
  //  - Not applied to case class or object
  //  - Generates sensible code for variants
  //  - Object format not correct (subclasses via reflection)

  // TODO [AA] Should include `shouldCompile` and `shouldNotCompile` tests for
  //  the error cases.

}
