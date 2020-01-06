package org.enso.graph

import org.enso.graph.definition.Macro.{component, field}
import org.enso.graph.{Graph => PrimGraph}
import org.scalatest.{FlatSpec, Matchers}
import shapeless.test.illTyped

class FieldMacroTest extends FlatSpec with Matchers {

  // == Components for Tests ==================================================
  @component case class Nodes() { type Node[G <: Graph] }
  @component case class Edges() { type Edge[G <: Graph] }

  "The `@field` macro" should "work for single fields" in {
    "@field case class Shape[G <: Graph](source: Node[G], target: Node[G])" should compile
  }

  "The `@field` macro" should "not explicitly depend on the graph type name" in {
    "@field case class Shape[G <: PrimGraph](source: Node[G], target: Node[G])" should compile
  }

  "The `@field` macro" should "work for variant fields" in {
    "@field object Shape {\n  type G = PrimGraph\n  case class Null()\n  case class App(fn: Edge[G], argTest: Edge[G])}" should compile
  }

  "Access to companion objects for fields" should "work as expected" in {
    "@field object Shape {\n  type G = Graph\n  case class Null()\n  case class App[G <: Graph](fn: Edge[G], argTest: Edge[G])}\nval test = Shape.Null.any" should compile
  }

  "The `@field` macro" should "not allow application to non case classes" in {
    illTyped(
      "@field class Shape",
      "@field must be applied to a case class or object"
    )
  }

  "The `@field` macro" should "not allow application to invalid constructs" in {
    illTyped(
      "@field type foo",
      "The @field macro only operates on case classes"
    )
  }

  "The `@field` macro" should "error on variants without branches" in {
    illTyped(
      "@field object Shape{\n  type G = Graph}",
      "A variant must contain at least one case"
    )
  }

  "The `@field` macro" should "error on variants that do not define `type G`" in {
    illTyped(
      "@field object Shape{\n  case class Null()}",
      "You must define a type named `G` in your variant that defines the graph type name"
    )
  }
}
