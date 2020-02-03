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
  //  - Copy subsection of graph
  //  - Check equality for subsection of graph
  //  - Improve the way construction errors are handled (use Either and an err
  //    node on failure)

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

      /** Creates a node representing an import statement.
        *
        * @param segments the segments of the import path
        * @param location the source location of the import statement
        * @return a node representing the import statement
        */
      def `import`(
        segments: CoreNode,
        location: AstLocation
      ): Try[RefinedNode[NodeShape.Import]] = {
        if (Utility.isListNode(segments)) {
          val node = CoreDef.Node.addRefined[NodeShape.Import]

          val segmentsLink = Link.make(node, segments)

          CoreDef.Node.addParent(segments, segmentsLink)

          node.segments = segmentsLink
          node.location = location

          Success(node)
        } else {
          Failure(
            new Exception.NotValidList(
              s"Import segments must be a valid meta list but node " +
              s"at index ${segments.ix} is not."
            )
          )
        }
      }

      /** Creates a node representing a top-level binding.
        *
        * This node does not represent the binding itself, but only serves to
        * represent the connection between the binding and its containing
        * module.
        *
        * @param module the module in which [[binding]] is defined
        * @param binding the binding itself
        * @param location the source location of the binding
        * @return a node representing the top-level binding
        */
      def topLevelBinding(
        module: CoreNode,
        binding: CoreNode,
        location: AstLocation
      ): Try[RefinedNode[NodeShape.TopLevelBinding]] = {
        binding match {
          case NodeShape.Binding.any(_) =>
            val node = CoreDef.Node.addRefined[NodeShape.TopLevelBinding]

            val moduleLink  = Link.make(node, module)
            val bindingLink = Link.make(node, binding)

            CoreDef.Node.addParent(module, moduleLink)
            CoreDef.Node.addParent(binding, bindingLink)

            node.module   = moduleLink
            node.binding  = bindingLink
            node.location = location

            Success(node)
          case _ =>
            Failure(
              new Exception.NotBinding(
                s"Node passed as `binding` must have" +
                s"binding shape but node at ${binding.ix} does not."
              )
            )
        }
      }

      // === Type Definitions =================================================

      /** Creates a node representing an atom definition.
        *
        * @param name the atom's name
        * @param args the atom's arguments
        * @param location the source location of the atom
        * @return a node representing an atom definition for [[name]]
        */
      def atomDef(
        name: CoreNode,
        args: CoreNode,
        location: AstLocation
      ): Try[RefinedNode[NodeShape.AtomDef]] = {
        if (Utility.isListNode(name)) {
          val node = CoreDef.Node.addRefined[NodeShape.AtomDef]

          val nameLink = Link.make(node, name)
          val argsLink = Link.make(node, args)

          CoreDef.Node.addParent(name, nameLink)
          CoreDef.Node.addParent(args, argsLink)

          node.name     = nameLink
          node.args     = argsLink
          node.location = location

          Success(node)
        } else {
          Failure(
            new Exception.NotValidList(
              s"Args must be provided as a valid list, but the node at " +
              s"${args.ix} is not."
            )
          )
        }
      }

      /** Creates a node representing a complex type definition.
        *
        * @param name the name of the type definition
        * @param typeParams the type parameters
        * @param body the body of the definition
        * @param location the source location of the definition
        * @return a node representing the type definition for [[name]]
        */
      def typeDef(
        name: CoreNode,
        typeParams: CoreNode,
        body: CoreNode,
        location: AstLocation
      ): Try[RefinedNode[NodeShape.TypeDef]] = {
        if (Utility.isListNode(typeParams) && Utility.isListNode(body)) {
          val node = CoreDef.Node.addRefined[NodeShape.TypeDef]

          val nameLink       = Link.make(node, name)
          val typeParamsLink = Link.make(node, typeParams)
          val bodyLink       = Link.make(node, body)

          CoreDef.Node.addParent(name, nameLink)
          CoreDef.Node.addParent(typeParams, typeParamsLink)
          CoreDef.Node.addParent(body, bodyLink)

          node.name       = nameLink
          node.typeParams = typeParamsLink
          node.body       = bodyLink
          node.location   = location

          Success(node)
        } else {
          Failure(
            new Exception.NotValidList(
              s"Type params and body must both be valid meta lists, but the " +
              s"node at ${typeParams.ix} or ${body.ix} is not."
            )
          )
        }
      }

      // === Typing ===========================================================

      /** Creates a node representing the ascription of a type to a value.
        *
        * The signature is an entirely arbitrary Enso expression, as required by
        * the language's syntactic unification.
        *
        * @param typed the expression being ascribed a type
        * @param sig the type being ascribed to [[typed]]
        * @param location the source location of the ascription
        * @return a node representing the ascription of the type represented by
        *         [[sig]] to [[typed]]
        */
      def typeAscription(
        typed: CoreNode,
        sig: CoreNode,
        location: AstLocation
      ): RefinedNode[NodeShape.TypeAscription] = {
        val node = CoreDef.Node.addRefined[NodeShape.TypeAscription]

        val typedLink = Link.make(node, typed)
        val sigLink   = Link.make(node, sig)

        CoreDef.Node.addParent(typed, typedLink)
        CoreDef.Node.addParent(sig, sigLink)

        node.typed    = typedLink
        node.sig      = sigLink
        node.location = location

        node
      }

      /** Creates a node representing the ascription of a monadic context to a
        * value (using the `in` keyword).
        *
        * @param typed the expression being ascribed a context
        * @param context the context being ascribed to [[typed]]
        * @param location the source location of the ascription
        * @return a node representing the ascription of the context [[context]]
        *         to the expression [[typed]]
        */
      def contextAscription(
        typed: CoreNode,
        context: CoreNode,
        location: AstLocation
      ): RefinedNode[NodeShape.ContextAscription] = {
        val node = CoreDef.Node.addRefined[NodeShape.ContextAscription]

        val typedLink   = Link.make(node, typed)
        val contextLink = Link.make(node, context)

        CoreDef.Node.addParent(typed, typedLink)
        CoreDef.Node.addParent(context, contextLink)

        node.typed    = typedLink
        node.context  = contextLink
        node.location = location

        node
      }

      /** Creates a node representing a typeset member.
        *
        * At most two of [[label]], [[memberType]] and [[value]] may be
        * [[NodeShape.Empty]].
        *
        * @param label the label of the member, if provided
        * @param memberType the type of the member, if provided
        * @param value the value of the member, if provided
        * @param location the source location of the member definition
        * @return a node representing a typeset member called [[label]] with
        *         type [[memberType]] and default value [[value]]
        */
      def typesetMember(
        label: CoreNode,
        memberType: CoreNode,
        value: CoreNode,
        location: AstLocation
      ): RefinedNode[NodeShape.TypesetMember] = {
        val node = CoreDef.Node.addRefined[NodeShape.TypesetMember]

        val labelLink      = Link.make(node, label)
        val memberTypeLink = Link.make(node, memberType)
        val valueLink      = Link.make(node, value)

        CoreDef.Node.addParent(label, labelLink)
        CoreDef.Node.addParent(memberType, memberTypeLink)
        CoreDef.Node.addParent(value, valueLink)

        node.label      = labelLink
        node.memberType = memberTypeLink
        node.value      = valueLink
        node.location   = location

        node
      }

      /** Creates a node representing the typeset subsumption operator `<:`.
        *
        * This construct does not represent a user-facing language element at
        * this time.
        *
        * @param left the left operand
        * @param right the right operand
        * @param location the location in the source to which the operator
        *                 corresponds
        * @return a node representing the judgement that [[left]] `<:` [[right]]
        */
      def typesetSubsumption(
        left: CoreNode,
        right: CoreNode,
        location: AstLocation
      ): RefinedNode[NodeShape.TypesetSubsumption] = {
        val node = CoreDef.Node.addRefined[NodeShape.TypesetSubsumption]

        val leftLink  = Link.make(node, left)
        val rightLink = Link.make(node, right)

        CoreDef.Node.addParent(left, leftLink)
        CoreDef.Node.addParent(right, rightLink)

        node.left     = leftLink
        node.right    = rightLink
        node.location = location

        node
      }

      /** Creates a node representing the typeset equality operator `~`.
        *
        * This construct does not represent a user-facing language element at
        * this time.
        *
        * @param left the left operand
        * @param right the right operand
        * @param location the location in the source to which the operator
        *                 corresponds
        * @return a node representing the judgement that [[left]] `~` [[right]]
        */
      def typesetEquality(
        left: CoreNode,
        right: CoreNode,
        location: AstLocation
      ): RefinedNode[NodeShape.TypesetEquality] = {
        val node = CoreDef.Node.addRefined[NodeShape.TypesetEquality]

        val leftLink  = Link.make(node, left)
        val rightLink = Link.make(node, right)

        CoreDef.Node.addParent(left, leftLink)
        CoreDef.Node.addParent(right, rightLink)

        node.left     = leftLink
        node.right    = rightLink
        node.location = location

        node
      }

      /** Creates a node representing the typeset concatenation operator `,`.
        *
        * @param left the left operand
        * @param right the right operand
        * @param location the location in the source to which the operator
        *                 corresponds
        * @return a node representing the judgement of [[left]] `,` [[right]]
        */
      def typesetConcat(
        left: CoreNode,
        right: CoreNode,
        location: AstLocation
      ): RefinedNode[NodeShape.TypesetConcat] = {
        val node = CoreDef.Node.addRefined[NodeShape.TypesetConcat]

        val leftLink  = Link.make(node, left)
        val rightLink = Link.make(node, right)

        CoreDef.Node.addParent(left, leftLink)
        CoreDef.Node.addParent(right, rightLink)

        node.left     = leftLink
        node.right    = rightLink
        node.location = location

        node
      }

      /** Creates a node representing the typeset union operator `|`.
        *
        * @param left the left operand
        * @param right the right operand
        * @param location the location in the source to which the operator
        *                 corresponds
        * @return a node representing the judgement of [[left]] `|` [[right]]
        */
      def typesetUnion(
        left: CoreNode,
        right: CoreNode,
        location: AstLocation
      ): RefinedNode[NodeShape.TypesetUnion] = {
        val node = CoreDef.Node.addRefined[NodeShape.TypesetUnion]

        val leftLink  = Link.make(node, left)
        val rightLink = Link.make(node, right)

        CoreDef.Node.addParent(left, leftLink)
        CoreDef.Node.addParent(right, rightLink)

        node.left     = leftLink
        node.right    = rightLink
        node.location = location

        node
      }

      /** Creates a node representing the typeset intersection operator `&`.
        *
        * @param left the left operand
        * @param right the right operand
        * @param location the location in the source to which the operator
        *                 corresponds
        * @return a node representing the judgement of [[left]] `&` [[right]]
        */
      def typesetIntersection(
        left: CoreNode,
        right: CoreNode,
        location: AstLocation
      ): RefinedNode[NodeShape.TypesetIntersection] = {
        val node = CoreDef.Node.addRefined[NodeShape.TypesetIntersection]

        val leftLink  = Link.make(node, left)
        val rightLink = Link.make(node, right)

        CoreDef.Node.addParent(left, leftLink)
        CoreDef.Node.addParent(right, rightLink)

        node.left     = leftLink
        node.right    = rightLink
        node.location = location

        node
      }

      /** Creates a node representing the typeset subtraction operator `\`.
        *
        * @param left the left operand
        * @param right the right operand
        * @param location the location in the source to which the operator
        *                 corresponds
        * @return a node representing the judgement of [[left]] `\` [[right]]
        */
      def typesetSubtraction(
        left: CoreNode,
        right: CoreNode,
        location: AstLocation
      ): RefinedNode[NodeShape.TypesetSubtraction] = {
        val node = CoreDef.Node.addRefined[NodeShape.TypesetSubtraction]

        val leftLink  = Link.make(node, left)
        val rightLink = Link.make(node, right)

        CoreDef.Node.addParent(left, leftLink)
        CoreDef.Node.addParent(right, rightLink)

        node.left     = leftLink
        node.right    = rightLink
        node.location = location

        node
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

      def functionDef(
        name: CoreNode,
        args: CoreNode,
        body: CoreNode,
        location: AstLocation
      ): Try[RefinedNode[NodeShape.FunctionDef]] = {
        if (Utility.isListNode(args)) {
          val node = CoreDef.Node.addRefined[NodeShape.FunctionDef]

          val nameLink = Link.make(node, name)
          val argsLink = Link.make(node, args)
          val bodyLink = Link.make(node, body)

          CoreDef.Node.addParent(name, nameLink)
          CoreDef.Node.addParent(args, argsLink)
          CoreDef.Node.addParent(body, bodyLink)

          node.name     = nameLink
          node.args     = argsLink
          node.body     = bodyLink
          node.location = location

          Success(node)
        } else {
          Failure(
            new Exception.NotValidList(
              s"Args must be a valid meta list, but node at ${args.ix} is not."
            )
          )
        }
      }
    }

    /** Exceptions used by the core graph. */
    object Exception {
      class InvalidListConstruction(val message: String) extends Throwable
      class NotValidList(val message: String)            extends Throwable
      class NotBinding(val message: String)              extends Throwable
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
