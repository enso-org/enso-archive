package org.enso.core

import org.enso.graph.definition.Macro.{component, field, genGraph, opaque}
import org.enso.graph.{Sized, VariantIndexed, Graph => PrimGraph}
import shapeless.{::, HNil}

// TODO [AA] Top-level bindings need a module link
object CoreGraph {
  @genGraph object Definition {

    // ==========================================================================
    // === The Graph Definition =================================================
    // ==========================================================================

    /** This type denotes the core graph itself. */
    case class CoreGraph() extends PrimGraph

    /** The list of components that make up a [[CoreGraph]]. */
    implicit def components =
      new PrimGraph.Component.List[CoreGraph] {
        type Out = Nodes :: Links :: HNil
      }

    // ==========================================================================
    // === Opaque Storage =======================================================
    // ==========================================================================

    /** Storage for string literals. */
    @opaque case class Literal(opaque: String)

    /** Storage for name literals. */
    @opaque case class Name(opaque: String)

    /** Storage for parents for a given node.
      *
      * An entry in the vector will be the index of an [[Edge]] in the graph that
      * has the containing node as its `target` field.
      */
    @opaque case class Parent(opaque: Vector[Int])

    // ==========================================================================
    // === Node =================================================================
    // ==========================================================================

    /** A node in the [[CoreGraph]]. */
    @component case class Nodes() { type Node[G <: PrimGraph] }

    /** The list of fields that a [[Node]] has in a [[CoreGraph]]. */
    implicit def nodeFields =
      new PrimGraph.Component.Field.List[CoreGraph, Nodes] {
        type Out = Node.Shape :: Node.ParentLinks :: Node.Location :: HNil
      }

    object Node {

      // ========================================================================
      // === Field Definitions ==================================================
      // ========================================================================

      /** A location describes which portion of the source code this particular
        * node in the graph represents.
        *
        * @param sourceStart the start position in the source code
        * @param sourceEnd the end position in the source code
        * @tparam G the graph type
        */
      @field case class Location[G <: PrimGraph](
        sourceStart: Int,
        sourceEnd: Int
      )

      /** This type represents all the incoming [[Link]]s to the current node.
        *
        * It should be noted that it _does not_ store the links directly. This
        * would only make sense if the link direction was reversed. Instead, it
        * holds unsafe references to the incoming link in the underlying graph.
        * These can be turned into the [[Link]]s directly by using
        * [[PrimGraph.GraphData.componentReferenceFromIndex()]].
        *
        * @param parents a vector containing the raw indices of the parent links
        * @tparam G the graph type
        */
      @field case class ParentLinks[G <: PrimGraph](
        parents: OpaqueData[Vector[Int], ParentStorage]
      )

      /** The shape of a node represents all the different forms that a node can
        * take.
        */
      @field object Shape {
        type G = PrimGraph

        // === Base Shapes ======================================================
        /** A representation of a node that has no particular shape. */
        case class Empty()

        /** A representation of a cons cell for building linked lists on the
          * graph.
          *
          * These should be used _very_ sparingly, if at all, but they provide a
          * way to store dynamically-sized core components providing they can be
          * broken down into statically sized components.
          *
          * The [[tail]] parameter should always point to either another node with
          * shape [[List]] or a node with shape [[Nil]].
          *
          * It should be noted that, given that each [[Node]] contains a field of
          * [[ParentLinks]], that constructing this properly provides a
          * doubly-linked list, as no [[List]] or [[Nil]] should have more than
          * one parent.
          *
          * @param head the current, arbitrary, element in the list
          * @param tail the rest of the list
          */
        case class List(head: Link[G], tail: Link[G])

        /** A representation of the end of a linked-list on the graph. */
        case class Nil()

        // === Literals =========================================================

        /** A raw literal is the basic literal type in the [[CoreGraph]].
          *
          * @param literal the literal text
          */
        case class RawLiteral(literal: OpaqueData[String, LiteralStorage])

        /** A representation of a numeric literal.
          *
          * @param number a link to the [[RawLiteral]] representing the number
          */
        case class NumericLiteral(number: Link[G])

        /** A representation of a textual literal.
          *
          * @param text a link to the [[RawLiteral]] representing the number
          */
        case class TextLiteral(text: Link[G])

        /** The raw representation of a name.
          *
          * @param literal the literal text of the name
          */
        case class NameLiteral(literal: OpaqueData[String, NameStorage])

        // === Names ============================================================

        /** The name of a module
          *
          * @param module a link to the name literal, represented as a
          *               [[NameLiteral]]
          */
        case class ModuleName(module: Link[G])

        /** The name of a constructor.
          *
          * @param name a link to the name literal, represented as a [[NameLiteral]].
          */
        case class ConstructorName(name: Link[G])

        // === Module ===========================================================

        /** The core representation of a top-level Enso module.
          *
          * @param name the name of the module
          * @param imports the module's imports as a [[List]], where each list
          *                member points to an import
          * @param definitions the module's definitions as a [[List]], where each
          *                    list member points to a binding
          */
        case class Module(name: Link[G], imports: Link[G], definitions: Link[G])

        /** An import statement.
          *
          * @param segments the segments of the import path, represented as a
          *                 [[NameLiteral]].
          */
        case class Import(segments: Link[G])

        case class TypeDef(name: Link[G], args: Link[G], body: Link[G])

        // === Function =========================================================

        case class Lambda()

        case class MethodDef(
          targetPath: Link[G],
          name: Link[G],
          functionDef: Link[G]
        )

        // === Structure ========================================================

        case class Block(expressions: Link[G], returnVal: Link[G])

        // === Typing ===========================================================

        case class Signature(typed: Link[G], sig: Link[G])

        // === Errors ===========================================================
      }

      // ========================================================================
      // === Utility Functions ==================================================
      // ========================================================================

      /** Sets the shape of the provided [[node]] to [[Shape]].
        *
        * @param node the node to set
        * @param ev evidence that [[Shape]] belongs to an indexed variant
        * @param graph the graph to mutate
        * @tparam Shape the shape to set the node to
        */
      def setShape[Shape <: Node.Shape](
        node: Node[CoreGraph]
      )(
        implicit ev: VariantIndexed[Node.Shape, Shape],
        graph: PrimGraph.GraphData[CoreGraph]
      ): Unit = {
        graph.unsafeSetVariantCase[Nodes, Node.Shape, Shape](node)
      }

      /** Checks whether a given node represents some kind of language error.
        *
        * @param node the node to check
        * @return `true` if [[node]] represents an errors `false` otherwise
        */
      def isErrorNode(node: Node[CoreGraph]): Boolean = {
        node match {
          case _ => false
        }
      }
    }

    // ==========================================================================
    // === Link =================================================================
    // ==========================================================================

    /** A link between nodes in the [[CoreGraph]]. */
    @component case class Links() { type Link[G <: PrimGraph] }

    /** The list of fields that a [[Link]] has in a [[CoreGraph]]. */
    implicit def linkFields =
      new PrimGraph.Component.Field.List[CoreGraph, Links] {
        type Out = Link.Shape :: HNil
      }

    object Link {

      // ========================================================================
      // === Field Definitions ==================================================
      // ========================================================================

      /** The shape of a link is static and represents a standard directional edge
        * in a graph.
        *
        * @param source the node at the start of the link
        * @param target the node at the end of the link
        * @tparam G the graph type
        */
      @field case class Shape[G <: PrimGraph](source: Node[G], target: Node[G])

      // ========================================================================
      // === Utility Functions ==================================================
      // ========================================================================
    }
  }
}
