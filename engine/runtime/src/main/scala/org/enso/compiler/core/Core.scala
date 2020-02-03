package org.enso.compiler.core

import org.enso.graph.{Graph => PrimGraph}
import org.enso.core.CoreGraph.{DefinitionGen => CoreDef}
import org.enso.core.CoreGraph.DefinitionGen.Node.{Shape => NodeShape}
import org.enso.syntax.text.{Location => AstLocation}

import scala.util.{Failure, Success, Try}

// TODO [AA] Detailed semantic descriptions for each node shape in future.

/** [[Core]] is the sophisticated internal representation supported by the
  * compiler.
  *
  * It is a structure designed to be amenable to program analysis and
  * transformation and features:
  * - High performance on a mutable graph structure.
  * - Mutable links to represent program structure.
  */
class Core {
  // TODO [AA] Need to present a nice interface
  //  - Assignment to children must update parent links for children.
  //  - Smart Constructors must handle the awful creation dance.
  //  - Accessors should look _through_ the edge, but there should still be a
  //    way to get the edge
  //  - Copy subsection of graph
  //  - Check equality for subsection of graph

  import CoreDef.Link.Shape._
  import CoreDef.Node.Location._
  import CoreDef.Node.ParentLinks._
  import CoreDef.Node.Shape._
  import PrimGraph.Component.Refined._

  // ==========================================================================
  // === Useful Type Aliases ==================================================
  // ==========================================================================

  type CoreGraph = CoreDef.CoreGraph
  type GraphData = PrimGraph.GraphData[CoreGraph]
  type CoreNode  = CoreDef.Node[CoreGraph]
  type CoreLink  = CoreDef.Link[CoreGraph]
  type RefinedNode[V <: CoreDef.Node.Shape] =
    PrimGraph.Component.Refined[NodeShape, V, CoreNode]

  // ==========================================================================
  // === Graph Storage ========================================================
  // ==========================================================================

  implicit val graph: GraphData = PrimGraph[CoreGraph]()

  implicit val literalStorage = CoreDef.LiteralStorage()
  implicit val nameStorage    = CoreDef.NameStorage()
  implicit val parentStorage  = CoreDef.ParentStorage()

  // ==========================================================================
  // === Node =================================================================
  // ==========================================================================

  /** Functionality for working with nodes. */
  object Node {

    /** Smart constructors to create nodes of various shapes. */
    //noinspection DuplicatedCode
    object Make {
      import Node.Conversions._

      // === Base Shapes ======================================================

      /** Creates a node that has no particular shape.
        *
        * @return an empty node
        */
      def empty(): RefinedNode[NodeShape.Empty] = {
        val node = CoreDef.Node.addRefined[NodeShape.Empty]

        node.location = Constants.invalidLocation

        node
      }

      /** Creates a representation of a cons cell for building linked lists on
        * the core graph.
        *
        * These should be used _very_ sparingly, if at all, but they provide a
        * way to store dynamically-sized core components providing they can be
        * broken down into statically sized components.
        *
        * The [[tail]] parameter should always point to either another node
        * with shape [[MetaList]] or a node with shape [[MetaNil]].
        *
        * It should be noted that, given that each [[Node]] contains a field
        * of [[ParentLinks]], that constructing this properly provides a
        * doubly-linked list, as no [[MetaList]] or [[MetaNil]] should have
        * more than one parent.
        *
        * The location contained in this node is _invalid_ as it does not
        * represent any location in the program source.
        *
        * @param head the current, arbitrary, element in the list
        * @param tail the rest of the list
        * @return a node representing an on-graph meta list
        */
      def metaList(
        head: CoreNode,
        tail: CoreNode
      ): Try[RefinedNode[NodeShape.MetaList]] = {
        if (Utility.isListNode(tail)) {
          val node = CoreDef.Node.addRefined[NodeShape.MetaList]

          val headLink = Link.make(node, head)
          val tailLink = Link.make(node, tail)

          CoreDef.Node.addParent(head, headLink)
          CoreDef.Node.addParent(tail, tailLink)

          node.head     = headLink
          node.tail     = tailLink
          node.location = Constants.invalidLocation

          Success(node)
        } else {
          Failure(
            new Exception.InvalidListConstruction(
              s"Provided tail with index ${tail.ix} is not a valid list cell."
            )
          )
        }
      }

      /** Creates a representation of the end of a linked-list on the core
        * graph.
        *
        * This should _only_ be used in conjunction with [[NodeShape.MetaList]].
        *
        * The location contained in this node is _invalid_ as it does not
        * represent any location in the program source.
        *
        * @return a node representing the end of an on-graph meta list
        */
      def metaNil(): RefinedNode[NodeShape.MetaNil] = {
        val node = CoreDef.Node.addRefined[NodeShape.MetaNil]

        node.location = Constants.invalidLocation
        node
      }

      /** Creates a representation of a meta-value `true` in the core graph.
        *
        * The location contained in this node is _invalid_ as it does not
        * represent any location in the program source.
        *
        * @return a node representing the on-graph metavalue `true`
        */
      def metaTrue(): RefinedNode[NodeShape.MetaTrue] = {
        val node = CoreDef.Node.addRefined[NodeShape.MetaTrue]

        node.location = Constants.invalidLocation
        node
      }

      /** Creates a representation of the meta-value `false` in the core graph.
        *
        * The location contained in this node is _invalid_ as it does not
        * represent any location in the program source.
        *
        * @return a node representing the on-graph metavalue `false`
        */
      def metaFalse(): RefinedNode[NodeShape.MetaFalse] = {
        val node = CoreDef.Node.addRefined[NodeShape.MetaFalse]

        node.location = Constants.invalidLocation
        node
      }

      // === Literals =========================================================

      /** Creates a node containing a numeric literal.
        *
        * @param number the literal number
        * @param location the source location for the literal
        * @return a numeric literal node representing [[number]]
        */
      def numericLiteral(
        number: String,
        location: AstLocation
      ): RefinedNode[NodeShape.NumericLiteral] = {
        val node = CoreDef.Node.addRefined[NodeShape.NumericLiteral]

        node.number   = number
        node.location = location

        node
      }

      /** Creates a node containing a textual literal.
        *
        * @param text the literal text
        * @param location the source location for the literal
        * @return a textual literal node representing [[text]]
        */
      def textLiteral(
        text: String,
        location: AstLocation
      ): RefinedNode[NodeShape.TextLiteral] = {
        val node = CoreDef.Node.addRefined[NodeShape.TextLiteral]

        node.text     = text
        node.location = location

        node
      }

      /** Creates a node containing a foreign code literal.
        *
        * @param code the foreign code
        * @param location the source location for the literal
        * @return a foreign code literal node representing [[code]]
        */
      def foreignCodeLiteral(
        code: String,
        location: AstLocation
      ): RefinedNode[NodeShape.ForeignCodeLiteral] = {
        val node = CoreDef.Node.addRefined[NodeShape.ForeignCodeLiteral]

        node.code     = code
        node.location = location

        node
      }

      // === Names ============================================================

      /** Creates a node representing a name.
        *
        * @param nameLiteral the literal representation of the name
        * @param location the source location for the name
        * @return a node representing the name [[nameLiteral]]
        */
      def name(
        nameLiteral: String,
        location: AstLocation
      ): RefinedNode[NodeShape.Name] = {
        val node = CoreDef.Node.addRefined[NodeShape.Name]

        node.nameLiteral = nameLiteral
        node.location    = location

        node
      }

      /** Creates a node representing a usage of `this`.
        *
        * @param location the source location of the `this` usage
        * @return a node representing the `this` usage at [[location]]
        */
      def thisName(location: AstLocation): RefinedNode[NodeShape.ThisName] = {
        val node = CoreDef.Node.addRefined[NodeShape.ThisName]

        node.location = location

        node
      }

      /** Creates a node representing a usage of `here`.
        *
        * @param location the source location of the `here` usage
        * @return a node representing the `here` usage at [[location]]
        */
      def hereName(location: AstLocation): RefinedNode[NodeShape.HereName] = {
        val node = CoreDef.Node.addRefined[NodeShape.HereName]

        node.location = location

        node
      }

      // === Module ===========================================================

      /** Creates a node representing a module definition.
        *
        * @param name the name of the module
        * @param imports the list of imports for the module, as a valid meta
        *                list
        * @param definitions the list of definitions in the module, as a valid
        *                    meta list
        * @param location the source location of the module definition
        * @return a node representing the module definition
        */
      def moduleDef(
        name: CoreNode,
        imports: CoreNode,
        definitions: CoreNode,
        location: AstLocation
      ): Try[RefinedNode[NodeShape.ModuleDef]] = {
        if (Utility.isListNode(imports) && Utility.isListNode(definitions)) {
          val node = CoreDef.Node.addRefined[NodeShape.ModuleDef]

          val nameLink        = Link.make(node, name)
          val importsLink     = Link.make(node, imports)
          val definitionsLink = Link.make(node, definitions)

          CoreDef.Node.addParent(name, nameLink)
          CoreDef.Node.addParent(imports, importsLink)
          CoreDef.Node.addParent(definitions, definitionsLink)

          node.name        = nameLink
          node.imports     = importsLink
          node.definitions = definitionsLink
          node.location    = location

          Success(node)
        } else {
          Failure(
            new Exception.NotValidList(
              s"Imports and definitions must be a valid meta list but node " +
              s"at index ${definitions.ix} or ${imports.ix} is not."
            )
          )
        }
      }

      // === Function =========================================================

      /** Creates a node representing a lambda expression, the `->` function
        * arrow.
        *
        * Please note that all lambdas in Enso are explicitly single-argument.
        *
        * @param arg the argument to the lambda
        * @param body the body of the lambda
        * @param location the location of this node in the program source
        * @return a lambda node with [[arg]] and [[body]] as its children
        */
      def lambda(
        arg: CoreNode,
        body: CoreNode,
        location: AstLocation
      ): RefinedNode[NodeShape.Lambda] = {
        val node = CoreDef.Node.addRefined[NodeShape.Lambda]

        val argLink  = Link.make(node, arg)
        val bodyLink = Link.make(node, body)

        CoreDef.Node.addParent(arg, argLink)
        CoreDef.Node.addParent(body, bodyLink)

        node.arg      = argLink
        node.body     = bodyLink
        node.location = location

        node
      }
    }

    /** Exceptions used by the core graph. */
    object Exception {
      class InvalidListConstruction(val message: String) extends Throwable
      class NotValidList(val message: String)            extends Throwable
    }

    /** Useful conversions between types that are used for Core nodes. */
    object Conversions {

      /** Converts the parser's location representation into Core's location
        * representation.
        *
        * @param location a location from the parser
        * @return the core representation of [[location]]
        */
      implicit def astLocationToNodeLocation(
        location: AstLocation
      ): CoreDef.Node.LocationVal[CoreGraph] = {
        CoreDef.Node.LocationVal(location.start, location.end)
      }
    }

    /** Constants for working with nodes. */
    object Constants {

      /** An invalid location in the pogram source. */
      val invalidSourceIndex: Int = -1
      val invalidLocation: CoreDef.Node.LocationVal[CoreGraph] =
        CoreDef.Node.LocationVal(invalidSourceIndex, invalidSourceIndex)
    }

    /** Utility functions for working with nodes. */
    object Utility {

      /** Checks if the provided node is a meta-level list node.
        *
        * A node is considered to be a list node when it has either the shape
        * [[NodeShape.MetaList]] or the shape [[NodeShape.MetaNil]].
        *
        * @param node the node to check
        * @return `true` if [[node]] is a list node, otherwise `false`
        */
      def isListNode(node: CoreNode): Boolean = {
        node match {
          case NodeShape.MetaList.any(_) => true
          case NodeShape.MetaNil.any(_)  => true
          case _                         => false
        }
      }
    }
  }

  // ==========================================================================
  // === Link =================================================================
  // ==========================================================================

  /** Functionality for working with links. */
  object Link {

    /**
      *
      * @param source
      * @param target
      * @return
      */
    def make(source: CoreNode, target: CoreNode): CoreLink = {
      val link = graph.addLink()

      link.source = source
      link.target = target

      link
    }

    def make(source: CoreNode): CoreLink = {
      val link      = graph.addLink()
      val emptyNode = Node.Make.empty()

      link.source = source
      link.target = emptyNode

      link
    }
  }
}
