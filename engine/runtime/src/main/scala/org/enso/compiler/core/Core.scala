package org.enso.compiler.core

import org.enso.graph.{Sized, Graph => PrimGraph}
import shapeless.nat._
import shapeless.{::, HNil}

// TODO [AA] Can we include the primitive/sugar distinction on the type level?
// TODO [AA] We may need to _re-export_ things instead

// TODO [AA] How do we store concrete types like `Location` in nodes?
// TODO [AA] Can I do a deeply-nested hierarchy without breaking things?
// TODO [AA] How to store lists of things?

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

//  @component case class Nodes() { type Node[G <: PrimGraph] }
  sealed case class Nodes() extends PrimGraph.Component
  type Node[G <: PrimGraph] = PrimGraph.Component.Ref[G, Nodes]
  implicit class GraphWithNodes[G <: PrimGraph](graph: PrimGraph.GraphData[G]) {
    def addNode()(implicit ev: PrimGraph.HasComponent[G, Nodes]): Node[G] = {
      graph.addComponent[Nodes]()
    }
  }

//  @component case class Links() { type Link[G <: PrimGraph] }
  sealed case class Links() extends PrimGraph.Component
  type Link[G <: PrimGraph] = PrimGraph.Component.Ref[G, Links]
  implicit class GraphWithLinks[G <: PrimGraph](graph: PrimGraph.GraphData[G]) {
    def addLink()(implicit ev: PrimGraph.HasComponent[G, Links]): Link[G] = {
      graph.addComponent[Links]()
    }
  }

  implicit def components =
    new PrimGraph.Component.List[CoreGraph] {
      type Out = HNil // TODO [AA] Actually add the proper components
    }

  implicit def nodeFields =
    new PrimGraph.Component.Field.List[CoreGraph, Nodes] {
      type Out = Node.ParentLink :: HNil // TODO [AA] Actually add the proper components
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
    sealed trait Shape extends PrimGraph.Component.Field
    object Shape {
      implicit def sized = new Sized[Shape] { type Out = _3 }

      sealed case class Null() extends Shape
      object Null {
        val any            = PrimGraph.Component.VariantMatcher[Shape, Null](0)
        implicit def sized = new Sized[Null] { type Out = _0 }
      }

      sealed case class Name() extends Shape
      // ???

      sealed case class App() extends Shape
      object App {
        implicit def sized = new Sized[App] { type Out = _2 }

        val any = PrimGraph.Component.VariantMatcher[Shape, App](1)

        def unapply[G <: PrimGraph, C <: PrimGraph.Component](
          arg: PrimGraph.Component.Ref[G, C]
        )(
          implicit graph: PrimGraph.GraphData[G],
          ev: PrimGraph.HasComponentField[G, C, Shape]
        ): Option[(Link[G], Link[G])] = {
          any.unapply(arg).map(t => (t.fn, t.arg))
        }

        implicit class AppInstance[G <: PrimGraph, C <: PrimGraph.Component](
          node: PrimGraph.Component.Refined[
            Shape,
            App,
            PrimGraph.Component.Ref[G, C]
          ]
        ) {
          def fn(
            implicit graph: PrimGraph.GraphData[G],
            ev: PrimGraph.HasComponentField[G, C, Shape]
          ): Link[G] = {
            PrimGraph.Component.Ref(
              graph.unsafeReadField[C, Shape](
                PrimGraph.Component.Refined.unwrap(node).ix,
                1
              )
            )
          }

          def fn_=(value: Link[G])(
            implicit graph: PrimGraph.GraphData[G],
            ev: PrimGraph.HasComponentField[G, C, Shape]
          ): Unit = {
            graph.unsafeWriteField[C, Shape](
              PrimGraph.Component.Refined.unwrap(node).ix,
              1,
              value.ix
            )
          }

          // TODO [AA] What if I want this to be a _list_ of arguments?
          def arg(
            implicit graph: PrimGraph.GraphData[G],
            ev: PrimGraph.HasComponentField[G, C, Shape]
          ): Link[G] = {
            PrimGraph.Component.Ref(
              graph.unsafeReadField[C, Shape](
                PrimGraph.Component.Refined.unwrap(node).ix,
                2
              )
            )
          }

          def arg_=(value: Link[G])(
            implicit graph: PrimGraph.GraphData[G],
            ev: PrimGraph.HasComponentField[G, C, Shape]
          ): Unit = {
            graph.unsafeWriteField[C, Shape](
              PrimGraph.Component.Refined.unwrap(node).ix,
              2,
              value.ix
            )
          }
        }
      }

      object Primitive {}
      object Sugar     {}
    }

    sealed case class ParentLink() extends PrimGraph.Component.Field
    object ParentLink {
      implicit def sized = new Sized[ParentLink] { type Out = _1 }

      implicit class ParentLinkInstance[
        G <: PrimGraph,
        C <: PrimGraph.Component
      ](
        node: PrimGraph.Component.Ref[G, C]
      ) {
        def parent(
          implicit graph: PrimGraph.GraphData[G],
          ev: PrimGraph.HasComponentField[G, C, ParentLink]
        ): Link[G] = {
          PrimGraph.Component.Ref(
            graph.unsafeReadField[C, ParentLink](node.ix, 0)
          )
        }

        def parent_=(value: Link[G])(
          implicit graph: PrimGraph.GraphData[G],
          ev: PrimGraph.HasComponentField[G, C, ParentLink]
        ): Unit = {
          graph.unsafeWriteField[C, ParentLink](node.ix, 0, value.ix)
        }
      }
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
