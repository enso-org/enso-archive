package org.enso.compiler.core

import org.enso.graph.{Graph => PrimGraph}
import org.enso.graph.definition.Macro.component
import shapeless.HNil

// TODO [AA] Can we include the primitive/sugar distinction on the type level?
// TODO [AA] We may need to _re-export_ things instead

// TODO [AA] How do we store concrete types like `Location` in nodes?
// TODO [AA] Can I do a deeply-nested hierarchy without breaking things?

/** [[Core]] is the sophisticated internal representation supported by the
  * compiler.
  *
  * It is a structure designed to be amenable to program analysis and
  * transformation and features:
  * - High performance on a mutable graph structure.
  * - High levels of type-safety to reduce the incidence of bugs.
  * - Mutable links to represent program structure.
  */
object Core {

  // ==========================================================================
  // === Graph ================================================================
  // ==========================================================================

  /** This the underlying graph representation for the core language. */
  case class CoreGraph() extends PrimGraph

  @component case class Nodes() { type Node[G <: PrimGraph] }
  @component case class Links() { type Link[G <: PrimGraph] }

//  sealed case class Nodes() extends PrimGraph.Component
//  type Node[G <: PrimGraph] = PrimGraph.Component.Ref[G, Nodes]
//  implicit class GraphWithNodes[G <: PrimGraph](graph: PrimGraph.GraphData[G]) {
//    def addNode()(implicit ev: PrimGraph.HasComponent[G, Nodes]): Node[G] = {
//      graph.addComponent[Nodes]()
//    }
//  }

//  sealed case class Links() extends PrimGraph.Component
//  type Link[G <: PrimGraph] = PrimGraph.Component.Ref[G, Links]
//  implicit class GraphWithLinks[G <: PrimGraph](graph: PrimGraph.GraphData[G]) {
//    def addLink()(implicit ev: PrimGraph.HasComponent[G, Links]): Link[G] = {
//      graph.addComponent[Links]()
//    }
//  }

  implicit def components =
    new PrimGraph.Component.List[CoreGraph] {
      type Out = HNil // TODO [AA] Actually add the proper components
    }

  implicit def nodeFields =
    new PrimGraph.Component.Field.List[CoreGraph, Nodes] {
      type Out = HNil // TODO [AA] Actually add the proper components
    }

  implicit def linkFields =
    new PrimGraph.Component.Field.List[CoreGraph, Links] {
      type Out = HNil // TODO [AA] Actually add the proper components
    }

  // ==========================================================================
  // === Node =================================================================
  // ==========================================================================

  /** Defines the fields of a node. */
  object Node {
    object Shape {
      object Primitive {}

      object Sugar {}
    }
  }

  // ==========================================================================
  // === Link =================================================================
  // ==========================================================================

  /** Defines the fields of a link. */
  object Link {}

  // ==========================================================================
  // === Components ===========================================================
  // ==========================================================================

  /** This contains the primitive components of [[Core]].
    *
    * The primitive components of [[Core]] are those which have no simpler
    * representation and are hence fundamental building blocks of the Enso
    * language. The idea is that most of the analysis performed on [[Core]]
    * takes place on this [[Primitive]] representation, thereby greatly
    * simplifying the number of constructs with which said analyses will need to
    * contend.
    *
    * Must contain:
    * - Module
    * - Name (should they be separate or not?)
    * - Block
    * - Lambda (+ arg definition)
    * - Assignment
    * - Atom definitions
    * - Type signatures
    * - Application (+ call arguments)
    * - Case expression
    * - Number and text literals
    * - Records
    * - Comment nodes (doc and disable)
    */
  object Primitive {}

  /** This contains all the components of [[Core]] that can be expressed in
    * terms of [[Core.Primitive]].
    *
    * While some analyses may need to contend with the constructs included
    * herein, most notably alias analysis, most analyses should not. To this
    * end, the desugaring passes should lower constructs from [[Sugar]] to
    * constructs from [[Primitive]] as soon as possible.
    *
    * Must contain:
    * - Grouping (parentheses)
    * - Sections
    * - `_` arguments
    * - Mixfix applications
    * - Complex function definitions
    * - Complex type definitions
    * - Foreign definitions
    * - Blank
    */
  object Sugar {}

  /** This contains all the components of [[Core]] that are used to represent
    * various kinds of error cases.
    *
    * [[Core.Error]] is used by both [[Core.Primitive]] and [[Core.Sugar]] to
    * represent erroneous conditions. These errors are then handled by passes
    * that can collate and display or otherwise process the errors.
    *
    * Must contain:
    * - Syntax errors
    * -
    */
  object Error {}
}
