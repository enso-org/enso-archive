package org.enso.compiler.core

import cats.data.NonEmptyList
import org.enso.core.CoreGraph.DefinitionGen.Node.{Shape => NodeShape}
import org.enso.core.CoreGraph.{DefinitionGen => CoreDef}
import org.enso.graph.{Graph => PrimGraph}
import org.enso.syntax.text.{AST, Location => AstLocation}

// TODO [AA] Detailed semantic descriptions for each node shape in future.
// TODO [AA] Eventually refactor graph macro generation so as to allow the
//  trait-extension based approach to implicit imports.

/** [[Core]] is the sophisticated internal representation supported by the
  * compiler.
  *
  * It is a structure designed to be amenable to program analysis and
  * transformation and features:
  * - High performance on a mutable graph structure.
  * - Mutable links to represent program structure.
  *
  * To use core properly you will need to have the following imports in scope.
  * These serve to bring the correct set of implicits into scope:
  *
  * {{{
  *   import Core._
  *   import CoreDef.Link.Shape._
  *   import CoreDef.Node.Location._
  *   import CoreDef.Node.ParentLinks._
  *   import CoreDef.Node.Shape._
  *   import org.enso.core.CoreGraph.{DefinitionGen => CoreDef}
  *   import org.enso.graph.{Graph => PrimGraph}
  *   import PrimGraph.Component.Refined._
  * }}}
  */
class Core {
  // TODO [AA] Need to present a nice interface
  //  - Copy subsection of graph
  //  - Check equality for subsection of graph

  // ==========================================================================
  // === Graph Storage ========================================================
  // ==========================================================================

  implicit val graph: Core.GraphData = PrimGraph[Core.Graph]()

  implicit val literalStorage: Core.LiteralStorage = CoreDef.LiteralStorage()
  implicit val nameStorage: Core.NameStorage       = CoreDef.NameStorage()
  implicit val parentStorage: Core.ParentStorage   = CoreDef.ParentStorage()
  implicit val astStorage: Core.AstStorage         = CoreDef.AstStorage()

}
object Core {

  import CoreDef.Link.Shape._
  import CoreDef.Node.Location._
  import CoreDef.Node.ParentLinks._
  import CoreDef.Node.Shape._
  import PrimGraph.Component.Refined._

  // ==========================================================================
  // === Useful Type Aliases ==================================================
  // ==========================================================================

  type Graph     = CoreDef.CoreGraph
  type GraphData = PrimGraph.GraphData[Graph]

  type Node = CoreDef.Node[Graph]
  type Link = CoreDef.Link[Graph]
  type RefinedNode[V <: CoreDef.Node.Shape] =
    PrimGraph.Component.Refined[NodeShape, V, Node]

  type ErrorOrRefined[Err <: CoreDef.Node.Shape, T <: CoreDef.Node.Shape] =
    Either[RefinedNode[Err], RefinedNode[T]]
  type ConsErrOrT[T <: CoreDef.Node.Shape] =
    ErrorOrRefined[NodeShape.ConstructionError, T]

  type Location = CoreDef.Node.LocationVal[Graph]

  type LiteralStorage = CoreDef.LiteralStorage
  type NameStorage    = CoreDef.NameStorage
  type ParentStorage  = CoreDef.ParentStorage
  type AstStorage     = CoreDef.AstStorage

  // ==========================================================================
  // === Node =================================================================
  // ==========================================================================

  /** Functionality for working with nodes. */
  object Node {

    /** Smart constructors to create nodes of various shapes. */
    //noinspection DuplicatedCode
    object New {

      // === Base Shapes ======================================================

      /** Creates a node that has no particular shape.
        *
        * @return an empty node
        */
      def empty()(implicit core: Core): RefinedNode[NodeShape.Empty] = {
        val node = CoreDef.Node.addRefined[NodeShape.Empty]

        node.location = Constants.invalidLocation
        node.parents  = Vector()

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
        head: Node,
        tail: Node
      )(implicit core: Core): ConsErrOrT[NodeShape.MetaList] = {
        if (Utility.isListNode(tail)) {
          val node = CoreDef.Node.addRefined[NodeShape.MetaList]

          val headLink = Link.New.connected(node, head)
          val tailLink = Link.New.connected(node, tail)

          CoreDef.Node.addParent(head, headLink)
          CoreDef.Node.addParent(tail, tailLink)

          node.head     = headLink
          node.tail     = tailLink
          node.location = Constants.invalidLocation
          node.parents  = Vector()

          Right(node)
        } else {
          val errorElems = Utility.coreListFrom(tail)
          val errorNode  = constructionError(errorElems, tail.location)

          Left(errorNode)
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
      def metaNil()(implicit core: Core): RefinedNode[NodeShape.MetaNil] = {
        val node = CoreDef.Node.addRefined[NodeShape.MetaNil]

        node.location = Constants.invalidLocation
        node.parents  = Vector()

        node
      }

      /** Creates a representation of a meta-value `true` in the core graph.
        *
        * The location contained in this node is _invalid_ as it does not
        * represent any location in the program source.
        *
        * @return a node representing the on-graph metavalue `true`
        */
      def metaTrue()(implicit core: Core): RefinedNode[NodeShape.MetaTrue] = {
        val node = CoreDef.Node.addRefined[NodeShape.MetaTrue]

        node.location = Constants.invalidLocation
        node.parents  = Vector()

        node
      }

      /** Creates a representation of the meta-value `false` in the core graph.
        *
        * The location contained in this node is _invalid_ as it does not
        * represent any location in the program source.
        *
        * @return a node representing the on-graph metavalue `false`
        */
      def metaFalse()(implicit core: Core): RefinedNode[NodeShape.MetaFalse] = {
        val node = CoreDef.Node.addRefined[NodeShape.MetaFalse]

        node.location = Constants.invalidLocation
        node.parents  = Vector()

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
        location: Location
      )(implicit core: Core): RefinedNode[NodeShape.NumericLiteral] = {
        val node = CoreDef.Node.addRefined[NodeShape.NumericLiteral]

        node.number   = number
        node.location = location
        node.parents  = Vector()

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
        location: Location
      )(implicit core: Core): RefinedNode[NodeShape.TextLiteral] = {
        val node = CoreDef.Node.addRefined[NodeShape.TextLiteral]

        node.text     = text
        node.location = location
        node.parents  = Vector()

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
        location: Location
      )(implicit core: Core): RefinedNode[NodeShape.ForeignCodeLiteral] = {
        val node = CoreDef.Node.addRefined[NodeShape.ForeignCodeLiteral]

        node.code     = code
        node.location = location
        node.parents  = Vector()

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
        location: Location
      )(implicit core: Core): RefinedNode[NodeShape.Name] = {
        val node = CoreDef.Node.addRefined[NodeShape.Name]

        node.nameLiteral = nameLiteral
        node.location    = location
        node.parents     = Vector()

        node
      }

      /** Creates a node representing a usage of `this`.
        *
        * @param location the source location of the `this` usage
        * @return a node representing the `this` usage at [[location]]
        */
      def thisName(
        location: Location
      )(implicit core: Core): RefinedNode[NodeShape.ThisName] = {
        val node = CoreDef.Node.addRefined[NodeShape.ThisName]

        node.location = location
        node.parents  = Vector()

        node
      }

      /** Creates a node representing a usage of `here`.
        *
        * @param location the source location of the `here` usage
        * @return a node representing the `here` usage at [[location]]
        */
      def hereName(
        location: Location
      )(implicit core: Core): RefinedNode[NodeShape.HereName] = {
        val node = CoreDef.Node.addRefined[NodeShape.HereName]

        node.location = location
        node.parents  = Vector()

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
        name: Node,
        imports: Node,
        definitions: Node,
        location: Location
      )(implicit core: Core): ConsErrOrT[NodeShape.ModuleDef] = {
        if (Utility.isListNode(imports) && Utility.isListNode(definitions)) {
          val node = CoreDef.Node.addRefined[NodeShape.ModuleDef]

          val nameLink        = Link.New.connected(node, name)
          val importsLink     = Link.New.connected(node, imports)
          val definitionsLink = Link.New.connected(node, definitions)

          CoreDef.Node.addParent(name, nameLink)
          CoreDef.Node.addParent(imports, importsLink)
          CoreDef.Node.addParent(definitions, definitionsLink)

          node.name        = nameLink
          node.imports     = importsLink
          node.definitions = definitionsLink
          node.location    = location
          node.parents     = Vector()

          Right(node)
        } else {
          val errorElems =
            Utility.coreListFrom(NonEmptyList(imports, List(definitions)))
          val error = constructionError(
            errorElems,
            CoreDef.Node.LocationVal[Graph](
              imports.location.sourceStart,
              definitions.location.sourceEnd
            )
          )

          Left(error)
        }
      }

      /** Creates a node representing an import statement.
        *
        * @param segments the segments of the import path
        * @param location the source location of the import statement
        * @return a node representing the import statement
        */
      def `import`(
        segments: Node,
        location: Location
      )(implicit core: Core): ConsErrOrT[NodeShape.Import] = {
        if (Utility.isListNode(segments)) {
          val node = CoreDef.Node.addRefined[NodeShape.Import]

          val segmentsLink = Link.New.connected(node, segments)

          CoreDef.Node.addParent(segments, segmentsLink)

          node.segments = segmentsLink
          node.location = location
          node.parents  = Vector()

          Right(node)
        } else {
          val errList = Utility.coreListFrom(segments)
          val errNode = constructionError(errList, segments.location)

          Left(errNode)
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
        module: Node,
        binding: Node,
        location: Location
      )(implicit core: Core): ConsErrOrT[NodeShape.TopLevelBinding] = {
        binding match {
          case NodeShape.Binding.any(_) =>
            val node = CoreDef.Node.addRefined[NodeShape.TopLevelBinding]

            val moduleLink  = Link.New.connected(node, module)
            val bindingLink = Link.New.connected(node, binding)

            CoreDef.Node.addParent(module, moduleLink)
            CoreDef.Node.addParent(binding, bindingLink)

            node.module   = moduleLink
            node.binding  = bindingLink
            node.location = location
            node.parents  = Vector()

            Right(node)
          case _ =>
            Left(constructionError(binding, binding.location))
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
        name: Node,
        args: Node,
        location: Location
      )(implicit core: Core): ConsErrOrT[NodeShape.AtomDef] = {
        if (Utility.isListNode(args)) {
          val node = CoreDef.Node.addRefined[NodeShape.AtomDef]

          val nameLink = Link.New.connected(node, name)
          val argsLink = Link.New.connected(node, args)

          CoreDef.Node.addParent(name, nameLink)
          CoreDef.Node.addParent(args, argsLink)

          node.name     = nameLink
          node.args     = argsLink
          node.location = location
          node.parents  = Vector()

          Right(node)
        } else {
          val errList = Utility.coreListFrom(args)
          val errNode = constructionError(errList, args.location)

          Left(errNode)
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
        name: Node,
        typeParams: Node,
        body: Node,
        location: Location
      )(implicit core: Core): ConsErrOrT[NodeShape.TypeDef] = {
        if (Utility.isListNode(typeParams) && Utility.isListNode(body)) {
          val node = CoreDef.Node.addRefined[NodeShape.TypeDef]

          val nameLink       = Link.New.connected(node, name)
          val typeParamsLink = Link.New.connected(node, typeParams)
          val bodyLink       = Link.New.connected(node, body)

          CoreDef.Node.addParent(name, nameLink)
          CoreDef.Node.addParent(typeParams, typeParamsLink)
          CoreDef.Node.addParent(body, bodyLink)

          node.name       = nameLink
          node.typeParams = typeParamsLink
          node.body       = bodyLink
          node.location   = location
          node.parents    = Vector()

          Right(node)
        } else {
          val errList =
            Utility.coreListFrom(NonEmptyList(typeParams, List(body)))
          val errNode = constructionError(
            errList,
            CoreDef.Node.LocationVal[Graph](
              typeParams.location.sourceStart,
              body.location.sourceEnd
            )
          )

          Left(errNode)
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
        typed: Node,
        sig: Node,
        location: Location
      )(implicit core: Core): RefinedNode[NodeShape.TypeAscription] = {
        val node = CoreDef.Node.addRefined[NodeShape.TypeAscription]

        val typedLink = Link.New.connected(node, typed)
        val sigLink   = Link.New.connected(node, sig)

        CoreDef.Node.addParent(typed, typedLink)
        CoreDef.Node.addParent(sig, sigLink)

        node.typed    = typedLink
        node.sig      = sigLink
        node.location = location
        node.parents  = Vector()

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
        typed: Node,
        context: Node,
        location: Location
      )(implicit core: Core): RefinedNode[NodeShape.ContextAscription] = {
        val node = CoreDef.Node.addRefined[NodeShape.ContextAscription]

        val typedLink   = Link.New.connected(node, typed)
        val contextLink = Link.New.connected(node, context)

        CoreDef.Node.addParent(typed, typedLink)
        CoreDef.Node.addParent(context, contextLink)

        node.typed    = typedLink
        node.context  = contextLink
        node.location = location
        node.parents  = Vector()

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
        label: Node,
        memberType: Node,
        value: Node,
        location: Location
      )(implicit core: Core): RefinedNode[NodeShape.TypesetMember] = {
        val node = CoreDef.Node.addRefined[NodeShape.TypesetMember]

        val labelLink      = Link.New.connected(node, label)
        val memberTypeLink = Link.New.connected(node, memberType)
        val valueLink      = Link.New.connected(node, value)

        CoreDef.Node.addParent(label, labelLink)
        CoreDef.Node.addParent(memberType, memberTypeLink)
        CoreDef.Node.addParent(value, valueLink)

        node.label      = labelLink
        node.memberType = memberTypeLink
        node.value      = valueLink
        node.location   = location
        node.parents    = Vector()

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
        left: Node,
        right: Node,
        location: Location
      )(implicit core: Core): RefinedNode[NodeShape.TypesetSubsumption] = {
        val node = CoreDef.Node.addRefined[NodeShape.TypesetSubsumption]

        val leftLink  = Link.New.connected(node, left)
        val rightLink = Link.New.connected(node, right)

        CoreDef.Node.addParent(left, leftLink)
        CoreDef.Node.addParent(right, rightLink)

        node.left     = leftLink
        node.right    = rightLink
        node.location = location
        node.parents  = Vector()

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
        left: Node,
        right: Node,
        location: Location
      )(implicit core: Core): RefinedNode[NodeShape.TypesetEquality] = {
        val node = CoreDef.Node.addRefined[NodeShape.TypesetEquality]

        val leftLink  = Link.New.connected(node, left)
        val rightLink = Link.New.connected(node, right)

        CoreDef.Node.addParent(left, leftLink)
        CoreDef.Node.addParent(right, rightLink)

        node.left     = leftLink
        node.right    = rightLink
        node.location = location
        node.parents  = Vector()

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
        left: Node,
        right: Node,
        location: Location
      )(implicit core: Core): RefinedNode[NodeShape.TypesetConcat] = {
        val node = CoreDef.Node.addRefined[NodeShape.TypesetConcat]

        val leftLink  = Link.New.connected(node, left)
        val rightLink = Link.New.connected(node, right)

        CoreDef.Node.addParent(left, leftLink)
        CoreDef.Node.addParent(right, rightLink)

        node.left     = leftLink
        node.right    = rightLink
        node.location = location
        node.parents  = Vector()

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
        left: Node,
        right: Node,
        location: Location
      )(implicit core: Core): RefinedNode[NodeShape.TypesetUnion] = {
        val node = CoreDef.Node.addRefined[NodeShape.TypesetUnion]

        val leftLink  = Link.New.connected(node, left)
        val rightLink = Link.New.connected(node, right)

        CoreDef.Node.addParent(left, leftLink)
        CoreDef.Node.addParent(right, rightLink)

        node.left     = leftLink
        node.right    = rightLink
        node.location = location
        node.parents  = Vector()

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
        left: Node,
        right: Node,
        location: Location
      )(implicit core: Core): RefinedNode[NodeShape.TypesetIntersection] = {
        val node = CoreDef.Node.addRefined[NodeShape.TypesetIntersection]

        val leftLink  = Link.New.connected(node, left)
        val rightLink = Link.New.connected(node, right)

        CoreDef.Node.addParent(left, leftLink)
        CoreDef.Node.addParent(right, rightLink)

        node.left     = leftLink
        node.right    = rightLink
        node.location = location
        node.parents  = Vector()

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
        left: Node,
        right: Node,
        location: Location
      )(implicit core: Core): RefinedNode[NodeShape.TypesetSubtraction] = {
        val node = CoreDef.Node.addRefined[NodeShape.TypesetSubtraction]

        val leftLink  = Link.New.connected(node, left)
        val rightLink = Link.New.connected(node, right)

        CoreDef.Node.addParent(left, leftLink)
        CoreDef.Node.addParent(right, rightLink)

        node.left     = leftLink
        node.right    = rightLink
        node.location = location
        node.parents  = Vector()

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
        arg: Node,
        body: Node,
        location: Location
      )(implicit core: Core): RefinedNode[NodeShape.Lambda] = {
        val node = CoreDef.Node.addRefined[NodeShape.Lambda]

        val argLink  = Link.New.connected(node, arg)
        val bodyLink = Link.New.connected(node, body)

        CoreDef.Node.addParent(arg, argLink)
        CoreDef.Node.addParent(body, bodyLink)

        node.arg      = argLink
        node.body     = bodyLink
        node.location = location
        node.parents  = Vector()

        node
      }

      /** Creates a node representing a function definition.
        *
        * @param name the name of the function being defined
        * @param args the arguments to the function being defined
        * @param body the body of the function being defined
        * @param location the source location of the function definition
        * @return a node representing a function defined for [[name]]
        */
      def functionDef(
        name: Node,
        args: Node,
        body: Node,
        location: Location
      )(implicit core: Core): ConsErrOrT[NodeShape.FunctionDef] = {
        if (Utility.isListNode(args)) {
          val node = CoreDef.Node.addRefined[NodeShape.FunctionDef]

          val nameLink = Link.New.connected(node, name)
          val argsLink = Link.New.connected(node, args)
          val bodyLink = Link.New.connected(node, body)

          CoreDef.Node.addParent(name, nameLink)
          CoreDef.Node.addParent(args, argsLink)
          CoreDef.Node.addParent(body, bodyLink)

          node.name     = nameLink
          node.args     = argsLink
          node.body     = bodyLink
          node.location = location
          node.parents  = Vector()

          Right(node)
        } else {
          val errList = Utility.coreListFrom(args)
          val errNode = constructionError(errList, args.location)

          Left(errNode)
        }
      }

      /** Creates a node representing a method definition
        *
        * @param targetPath the method path for the definition
        * @param name the method name
        * @param function the implementation of the method. This must either be
        *                 a [[NodeShape.Lambda]] or a [[NodeShape.FunctionDef]]
        * @param location the source location of the method definition
        * @return a node that defines method [[name]] on [[path]]
        */
      def methodDef(
        targetPath: Node,
        name: Node,
        function: Node,
        location: Location
      )(implicit core: Core): ConsErrOrT[NodeShape.MethodDef] = {
        val bodyIsValid = function match {
          case NodeShape.FunctionDef.any(_) => true
          case NodeShape.Lambda.any(_)      => true
          case _                            => false
        }

        if (bodyIsValid) {
          val node = CoreDef.Node.addRefined[NodeShape.MethodDef]

          val targetPathLink = Link.New.connected(node, targetPath)
          val nameLink       = Link.New.connected(node, name)
          val functionLink   = Link.New.connected(node, function)

          CoreDef.Node.addParent(targetPath, targetPathLink)
          CoreDef.Node.addParent(name, nameLink)
          CoreDef.Node.addParent(function, functionLink)

          node.targetPath = targetPathLink
          node.name       = nameLink
          node.function   = functionLink
          node.location   = location
          node.parents    = Vector()

          Right(node)
        } else {
          val errList = Utility.coreListFrom(function)
          val errNode = constructionError(errList, function.location)

          Left(errNode)
        }
      }

      // === Definition-Site Argument Types ===================================

      /** Creates a node representing an ignored argument.
        *
        * An ignored argument is one that is not used in the body and is
        * explicitly ignored so as not to introduce warnings.
        *
        * @param location the location of the ignored argument usage
        * @return a node representing an ignored argument
        */
      def ignoredArgument(
        location: Location
      )(implicit core: Core): RefinedNode[NodeShape.IgnoredArgument] = {
        val node = CoreDef.Node.addRefined[NodeShape.IgnoredArgument]

        node.location = location

        node
      }

      /** Creates a node representing an argument from a function definition
        * site.
        *
        * @param name the name of the argument
        * @param suspended whether or not the argument is suspended, as either
        *                  [[NodeShape.MetaTrue]] or [[NodeShape.MetaFalse]]
        * @param default the default value for the argument, if present
        * @param location the source location of the argument
        * @return a node representing a definition site argument called [[name]]
        */
      def definitionArgument(
        name: Node,
        suspended: Node,
        default: Node,
        location: Location
      )(implicit core: Core): ConsErrOrT[NodeShape.DefinitionArgument] = {
        val suspendedIsBool = suspended match {
          case NodeShape.MetaTrue.any(_)  => true
          case NodeShape.MetaFalse.any(_) => true
          case _                          => false
        }

        if (suspendedIsBool) {
          val node = CoreDef.Node.addRefined[NodeShape.DefinitionArgument]

          val nameLink      = Link.New.connected(node, name)
          val suspendedLink = Link.New.connected(node, name)
          val defaultLink   = Link.New.connected(node, name)

          CoreDef.Node.addParent(name, nameLink)
          CoreDef.Node.addParent(suspended, suspendedLink)
          CoreDef.Node.addParent(default, defaultLink)

          node.name      = nameLink
          node.suspended = suspendedLink
          node.default   = defaultLink
          node.location  = location
          node.parents   = Vector()

          Right(node)
        } else {
          val errList = Utility.coreListFrom(suspended)
          val errNode = constructionError(errList, suspended.location)

          Left(errNode)
        }
      }

      // === Applications =====================================================

      /** Creates a node representing a function application.
        *
        * Please note that _all_ functions in Enso are curried by default. and
        * applications to multiple arguments are represented in [[Core]] as
        * single-argument functions.
        *
        * @param function the function being applied
        * @param argument the argument to [[function]]
        * @param location the soure location for the application
        * @return a node that applies [[function]] to [[argument]]
        */
      def application(
        function: Node,
        argument: Node,
        location: Location
      )(implicit core: Core): RefinedNode[NodeShape.Application] = {
        val node = CoreDef.Node.addRefined[NodeShape.Application]

        val functionLink = Link.New.connected(node, function)
        val argumentLink = Link.New.connected(node, argument)

        CoreDef.Node.addParent(function, functionLink)
        CoreDef.Node.addParent(argument, argumentLink)

        node.function = functionLink
        node.argument = argumentLink
        node.location = location
        node.parents  = Vector()

        node
      }

      /** Creates a node representing an infix application.
        *
        * @param left the left argument to the operator
        * @param operator the operator being applied
        * @param right the right argument to the operator
        * @param location the source location of the infox application
        * @return a node representing the application of [[operator]] to
        *         [[left]] and [[right]]
        */
      def infixApplication(
        left: Node,
        operator: Node,
        right: Node,
        location: Location
      )(implicit core: Core): RefinedNode[NodeShape.InfixApplication] = {
        val node = CoreDef.Node.addRefined[NodeShape.InfixApplication]

        val leftLink     = Link.New.connected(node, left)
        val operatorLink = Link.New.connected(node, operator)
        val rightLink    = Link.New.connected(node, right)

        CoreDef.Node.addParent(left, leftLink)
        CoreDef.Node.addParent(operator, operatorLink)
        CoreDef.Node.addParent(right, rightLink)

        node.left     = leftLink
        node.operator = operatorLink
        node.right    = rightLink
        node.location = location
        node.parents  = Vector()

        node
      }

      /** Creates a node representing a left operator section.
        *
        * @param arg the left argument to [[operator]]
        * @param operator the function being applied
        * @param location the source location of the application
        * @return a node representing the partial application of [[operator]] to
        *         [[arg]]
        */
      def leftSection(
        arg: Node,
        operator: Node,
        location: Location
      )(implicit core: Core): RefinedNode[NodeShape.LeftSection] = {
        val node = CoreDef.Node.addRefined[NodeShape.LeftSection]

        val argLink      = Link.New.connected(node, arg)
        val operatorLink = Link.New.connected(node, operator)

        CoreDef.Node.addParent(arg, argLink)
        CoreDef.Node.addParent(operator, operatorLink)

        node.arg      = argLink
        node.operator = operatorLink
        node.location = location
        node.parents  = Vector()

        node
      }

      /** Creates a node representing a right operator section.
        *
        * @param operator the function being applied
        * @param arg the right argument to [[operator]]
        * @param location the source location of the application
        * @return a node representing the partial application of [[operator]] to
        *         [[arg]]
        */
      def rightSection(
        operator: Node,
        arg: Node,
        location: Location
      )(implicit core: Core): RefinedNode[NodeShape.RightSection] = {
        val node = CoreDef.Node.addRefined[NodeShape.RightSection]

        val operatorLink = Link.New.connected(node, operator)
        val argLink      = Link.New.connected(node, arg)

        CoreDef.Node.addParent(operator, operatorLink)
        CoreDef.Node.addParent(arg, argLink)

        node.operator = operatorLink
        node.arg      = argLink
        node.location = location
        node.parents  = Vector()

        node
      }

      /** Creates a node representing a centre operator section.
        *
        * @param operator the function being partially applied
        * @param location the source location of the application
        * @return a node representing the partial application of [[operator]]
        */
      def centreSection(
        operator: Node,
        location: Location
      )(implicit core: Core): RefinedNode[NodeShape.CentreSection] = {
        val node = CoreDef.Node.addRefined[NodeShape.CentreSection]

        val operatorLink = Link.New.connected(node, operator)

        CoreDef.Node.addParent(operator, operatorLink)

        node.operator = operatorLink
        node.location = location
        node.parents  = Vector()

        node
      }

      /** A node representing a term being explicitly forced.
        *
        * An explicitly forced term is one where the user has explicitly called
        * the `force` operator on it. This is useful only while the compiler
        * does not _automatically_ handle suspensions and forcing.
        *
        * PLEASE NOTE: This is temporary and will be removed as soon as the
        * compiler is capable enough to not require it.
        *
        * @param expression the expression being forced
        * @param location the source location of the forced expression
        * @return a node representing [[expression]] being explicitly forced
        */
      def forcedTerm(
        expression: Node,
        location: Location
      )(implicit core: Core): RefinedNode[NodeShape.ForcedTerm] = {
        val node = CoreDef.Node.addRefined[NodeShape.ForcedTerm]

        val expressionLink = Link.New.connected(node, expression)

        CoreDef.Node.addParent(expression, expressionLink)

        node.expression = expressionLink
        node.location   = location
        node.parents    = Vector()

        node
      }

      // === Call-Site Argument Types =========================================

      /** Creates a node representing a lambda shorthand argument.
        *
        * A lambda shorthand argument is the name for the usage of `_` at a
        * function call-site, where it is syntax sugar for a lambda parameter to
        * that function.
        *
        * @param location the location of this argument in the source code
        * @return a node representing the `_` argument found at [[location]]
        */
      def lambdaShorthandArgument(
        location: Location
      )(implicit core: Core): RefinedNode[NodeShape.LambdaShorthandArgument] = {
        val node = CoreDef.Node.addRefined[NodeShape.LambdaShorthandArgument]

        node.location = location
        node.parents  = Vector()

        node
      }

      /** Creates a node representing an argument from a function call site.
        *
        * The expression must always be present, but the argument [[name]] may
        * be an instance of [[NodeShape.Empty]].
        *
        * @param expression the expression being passes as the argument
        * @param name the name of the argument, if present
        * @param location the source location of this argument
        * @return a node representing the use of [[expression]] as an argument
        *         to a function
        */
      def callSiteArgument(
        expression: Node,
        name: Node,
        location: Location
      )(implicit core: Core): RefinedNode[NodeShape.CallSiteArgument] = {
        val node = CoreDef.Node.addRefined[NodeShape.CallSiteArgument]

        val expressionLink = Link.New.connected(node, expression)
        val nameLink       = Link.New.connected(node, name)

        CoreDef.Node.addParent(expression, expressionLink)
        CoreDef.Node.addParent(name, nameLink)

        node.expression = expressionLink
        node.name       = nameLink
        node.location   = location
        node.parents    = Vector()

        node
      }

      /** Creates a node representing a usage of the function defaults
        * suspension operator `...`.
        *
        * @param location the source location of the operator usage
        * @return a node representing a usage of the `...` operator
        */
      def suspendDefaultsOperator(
        location: Location
      )(implicit core: Core): RefinedNode[NodeShape.SuspendDefaultsOperator] = {
        val node = CoreDef.Node.addRefined[NodeShape.SuspendDefaultsOperator]

        node.location = location
        node.parents  = Vector()

        node
      }

      // === Structure ========================================================

      /** Creates a node representing a block expression.
        *
        * @param expressions a valid meta list of expressions (should be
        *                    [[NodeShape.MetaNil]] if none are present
        * @param returnVal the final expression in the block
        * @param location the source location of the block
        * @return a representation of a block containing [[expressions]] and
        *         [[returnVal]]
        */
      def block(
        expressions: Node,
        returnVal: Node,
        location: Location
      )(implicit core: Core): ConsErrOrT[NodeShape.Block] = {
        if (Utility.isListNode(expressions)) {
          val node = CoreDef.Node.addRefined[NodeShape.Block]

          val expressionsLink = Link.New.connected(node, expressions)
          val returnValLink   = Link.New.connected(node, returnVal)

          CoreDef.Node.addParent(expressions, expressionsLink)
          CoreDef.Node.addParent(returnVal, returnValLink)

          node.expressions = expressionsLink
          node.returnVal   = returnValLink
          node.location    = location
          node.parents     = Vector()

          Right(node)
        } else {
          val errList = Utility.coreListFrom(expressions)
          val errNode = constructionError(errList, expressions.location)

          Left(errNode)
        }
      }

      /** Creates a node representing a binding of the form `name = expression`.
        *
        * @param name the name being bound to
        * @param expression the expression being bound to [[name]]
        * @param location the soure location of the binding
        * @return a representation of the binding of the result of
        *         [[expression]] to [[name]]
        */
      def binding(
        name: Node,
        expression: Node,
        location: Location
      )(implicit core: Core): RefinedNode[NodeShape.Binding] = {
        val node = CoreDef.Node.addRefined[NodeShape.Binding]

        val nameLink       = Link.New.connected(node, name)
        val expressionLink = Link.New.connected(node, expression)

        CoreDef.Node.addParent(name, nameLink)
        CoreDef.Node.addParent(expression, expressionLink)

        node.name       = nameLink
        node.expression = expressionLink
        node.location   = location
        node.parents    = Vector()

        node
      }

      // === Case Expression ==================================================

      /** Creates a node representing a case expression.
        *
        * @param scrutinee the expression being matched on
        * @param branches the branches doing the matching
        * @param location the soure location of the case expression
        * @return a node representing pattern matching on [[scrutinee]] using
        *         [[branches]]
        */
      def caseExpr(
        scrutinee: Node,
        branches: Node,
        location: Location
      )(implicit core: Core): ConsErrOrT[NodeShape.CaseExpr] = {
        if (Utility.isListNode(branches)) {
          val node = CoreDef.Node.addRefined[NodeShape.CaseExpr]

          val scrutineeLink = Link.New.connected(node, scrutinee)
          val branchesLink  = Link.New.connected(node, branches)

          CoreDef.Node.addParent(scrutinee, scrutineeLink)
          CoreDef.Node.addParent(branches, branchesLink)

          node.scrutinee = scrutineeLink
          node.branches  = branchesLink
          node.location  = location
          node.parents   = Vector()

          Right(node)
        } else {
          val errList = Utility.coreListFrom(branches)
          val errNode = constructionError(errList, branches.location)

          Left(errNode)
        }
      }

      /** Creates a node representing a branch in a case expression.
        *
        * @param pattern the pattern match for the branch
        * @param expression the expression that is executed if [[pattern]]
        *                   successfully matches the case scrutinee
        * @param location the source location of the case branch
        * @return a node representing a case branch matching [[pattern]]
        */
      def caseBranch(
        pattern: Node,
        expression: Node,
        location: Location
      )(implicit core: Core): RefinedNode[NodeShape.CaseBranch] = {
        val node = CoreDef.Node.addRefined[NodeShape.CaseBranch]

        val patternLink    = Link.New.connected(node, pattern)
        val expressionLink = Link.New.connected(node, expression)

        CoreDef.Node.addParent(pattern, patternLink)
        CoreDef.Node.addParent(expression, expressionLink)

        node.pattern    = patternLink
        node.expression = expressionLink
        node.location   = location
        node.parents    = Vector()

        node
      }

      /** Creates a node representing a structural pattern.
        *
        * A structural pattern is one that examines the _structure_ of the
        * scrutinee. However, as Enso is a dependently typed language, an
        * examination of the structure is also an examination of the type.
        *
        * @param matchExpression the expression representing the pattern
        * @param location the source location of the patttern
        * @return a node representing the structural match defined by
        *         [[matchExpression]]
        */
      def structuralPattern(
        matchExpression: Node,
        location: Location
      )(implicit core: Core): RefinedNode[NodeShape.StructuralPattern] = {
        val node = CoreDef.Node.addRefined[NodeShape.StructuralPattern]

        val matchExpressionLink = Link.New.connected(node, matchExpression)

        CoreDef.Node.addParent(matchExpression, matchExpressionLink)

        node.matchExpression = matchExpressionLink
        node.location        = location
        node.parents         = Vector()

        node
      }

      /** Creates a node representing a type-based pattern.
        *
        * A type-based pattern is one that purely examines the type of the
        * scrutinee, without including any structural elements.
        *
        * @param matchExpression the expression representing the pattern
        * @param location the source location of the pattern
        * @return a node representing the type-based match defined by
        *         [[matchExpression]]
        */
      def typePattern(
        matchExpression: Node,
        location: Location
      )(implicit core: Core): RefinedNode[NodeShape.TypePattern] = {
        val node = CoreDef.Node.addRefined[NodeShape.TypePattern]

        val matchExpressionLink = Link.New.connected(node, matchExpression)

        CoreDef.Node.addParent(matchExpression, matchExpressionLink)

        node.matchExpression = matchExpressionLink
        node.location        = location
        node.parents         = Vector()

        node
      }

      /** Creates a node representing a named pattern.
        *
        * A named pattern is one that renames the scrutinee in the pattern
        * branch.
        *
        * @param matchExpression the expression representing the pattern
        * @param location the soure location of the pattern
        * @return a node representing the type-based match defined by
        *         [[matchExpression]]
        */
      def namedPattern(
        matchExpression: Node,
        location: Location
      )(implicit core: Core): RefinedNode[NodeShape.NamedPattern] = {
        val node = CoreDef.Node.addRefined[NodeShape.NamedPattern]

        val matchExpressionLink = Link.New.connected(node, matchExpression)

        CoreDef.Node.addParent(matchExpression, matchExpressionLink)

        node.matchExpression = matchExpressionLink
        node.location        = location
        node.parents         = Vector()

        node
      }

      /** Creates a node representing a fallback pattern.
        *
        * A fallback pattern is also known as a catch-all, pattern, and will
        * unconditionally match any scrutinee.
        *
        * @param location the soure location of the pattern
        * @return a node representing the fallback pattern at [[location]]
        */
      def fallbackPattern(
        location: Location
      )(implicit core: Core): RefinedNode[NodeShape.FallbackPattern] = {
        val node = CoreDef.Node.addRefined[NodeShape.FallbackPattern]

        node.location = location
        node.parents  = Vector()

        node
      }

      // === Comments =========================================================

      /** Creates a node representing an entity with an associated doc comment.
        *
        * @param commented the entity that has the comment
        * @param doc the documentation associated with [[commented]]
        * @param location the source location of [[commented]] and its
        *                 associated doc
        * @return a node representing the association of [[doc]] to
        *         [[commented]]
        */
      def docComment(
        commented: Node,
        doc: Node,
        location: Location
      )(implicit core: Core): RefinedNode[NodeShape.DocComment] = {
        val node = CoreDef.Node.addRefined[NodeShape.DocComment]

        val commentedLink = Link.New.connected(node, commented)
        val docLink       = Link.New.connected(node, doc)

        CoreDef.Node.addParent(commented, commentedLink)
        CoreDef.Node.addParent(doc, docLink)

        node.commented = commentedLink
        node.doc       = docLink
        node.location  = location
        node.parents   = Vector()

        node
      }

      // === Foreign ==========================================================

      /** Creates a node representing a block of foreign code.
        *
        * @param language the programming language for which [[code]] is written
        * @param code the source code in [[language]] (must be a
        *             [[NodeShape.ForeignCodeLiteral]])
        * @param location the source location of the foreign code block
        * @return a node representing [[code]] in [[language]]
        */
      def foreignDefinition(
        language: Node,
        code: Node,
        location: Location
      )(implicit core: Core): ConsErrOrT[NodeShape.ForeignDefinition] = {
        code match {
          case NodeShape.ForeignCodeLiteral.any(_) =>
            val node = CoreDef.Node.addRefined[NodeShape.ForeignDefinition]

            val languageLink = Link.New.connected(node, language)
            val codeLink     = Link.New.connected(node, code)

            CoreDef.Node.addParent(language, languageLink)
            CoreDef.Node.addParent(code, codeLink)

            node.language = languageLink
            node.code     = codeLink
            node.location = location
            node.parents  = Vector()

            Right(node)
          case _ =>
            val errList = Utility.coreListFrom(code)
            val errNode = constructionError(errList, code.location)

            Left(errNode)
        }
      }

      // === Errors ===========================================================

      /** Creates a node representing a syntax error.
        *
        * @param errorAst the AST that is syntactically invalid
        * @return a node representing the syntax error described by [[errorAst]]
        */
      def syntaxError(
        errorAst: AST
      )(implicit core: Core): RefinedNode[NodeShape.SyntaxError] = {
        val node = CoreDef.Node.addRefined[NodeShape.SyntaxError]
        val errLocation: Location =
          errorAst.location
            .map(Conversions.astLocationToNodeLocation)
            .getOrElse(Constants.invalidLocation)

        node.errorAst = errorAst
        node.location = errLocation
        node.parents  = Vector()

        node
      }

      /** Creates a node representing an error that occurred when constructing
        * a [[Core]] expression.
        *
        * @param erroneousCore the core expression(s) that caused a problem
        * @param location the location at which the erroneous core occurred
        * @return a node representing an erroneous core expression
        */
      def constructionError(
        erroneousCore: Node,
        location: Location
      )(implicit core: Core): RefinedNode[NodeShape.ConstructionError] = {
        val node = CoreDef.Node.addRefined[NodeShape.ConstructionError]

        val erroneousCoreLink = Link.New.connected(node, erroneousCore)

        CoreDef.Node.addParent(erroneousCore, erroneousCoreLink)

        node.erroneousCore = erroneousCoreLink
        node.location      = location
        node.parents       = Vector()

        node
      }
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
      ): CoreDef.Node.LocationVal[Graph] = {
        CoreDef.Node.LocationVal(location.start, location.end)
      }
    }

    /** Constants for working with nodes. */
    object Constants {

      /** An invalid location in the pogram source. */
      val invalidSourceIndex: Int = -1
      val invalidLocation: CoreDef.Node.LocationVal[Graph] =
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
      def isListNode(node: Node)(implicit core: Core): Boolean = {
        node match {
          case NodeShape.MetaNil.any(_)  => true
          case NodeShape.MetaList.any(_) => true
          case _                         => false
        }
      }

      /** Constructs a meta list on the [[Core]] graph from a single core
        * expression.
        *
        * @param node the expression to turn into a valid list
        * @return a node representing the head of a meta-level list
        */
      def coreListFrom(
        node: Node
      )(implicit core: Core): RefinedNode[NodeShape.MetaList] = {
        coreListFrom(NonEmptyList(node, List()))
      }

      /** Constructs a meta list on the [[Core]] graph from a list of [[Core]]
        * nodes.
        *
        * @param nodes the nodes to make a list out of
        * @return a node representing the head of a meta-level list
        */
      def coreListFrom(
        nodes: NonEmptyList[Node]
      )(implicit core: Core): RefinedNode[NodeShape.MetaList] = {
        val nodesWithNil = nodes :+ New.metaNil().wrapped

        // Note [Unsafety in List Construction]
        val unrefinedMetaList =
          nodesWithNil.toList.reduceRight(
            (l, r) => New.metaList(l, r).right.get.wrapped
          )

        PrimGraph.Component.Refined
          .wrap[NodeShape, NodeShape.MetaList, Node](unrefinedMetaList)
      }

      /* Note [Unsafety in List Construction]
     * ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
     * This makes use of internal implementation details to know that calling
     * `right` here is always safe. The error condition for the `metaList`
     * constructor occurs when the `tail` argument doesn't point to a valid
     * list element, but here we construct that element directly and hence we
     * know that it is valid.
     *
     * Furthermore, we can unconditionally refine the type as we know that the
     * node we get back must be a MetaList, and we know that the input is not
     * empty.
     *
     * It also bears noting that for this to be safe we _must_ use a right
     * reduce, rather than a left reduce, otherwise the elements will not be
     * constructed properly. This does, however, mean that this can stack
     * overflow when provided with too many elements.
     */
    }
  }

  // ==========================================================================
  // === Link =================================================================
  // ==========================================================================

  /** Functionality for working with links. */
  object Link {
    object New {

      /** Makes a link with the provided source and target.
        *
        * @param source the start of the link
        * @param target the end of the link
        * @return a link from [[source]] to [[target]]
        */
      def connected(source: Node, target: Node)(implicit core: Core): Link = {
        val link = core.graph.addLink()

        link.source = source
        link.target = target

        link
      }

      /** Makes a link with only a source.
        *
        * The target is defaulted to a new empty node.
        *
        * @param source the start of the link
        * @return a link from [[source]] to a new [[NodeShape.Empty]] node
        */
      def disconnected(source: Node)(implicit core: Core): Link = {
        val link      = core.graph.addLink()
        val emptyNode = Node.New.empty()

        link.source = source
        link.target = emptyNode

        link
      }
    }
  }
  // ==========================================================================
  // === Implicits ============================================================
  // ==========================================================================

  /* Note [Implicit Conversions On Implicits]
   * ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
   * While the enforced usage of the compiler plugin 'splain' does much to make
   * debugging issues with implicit resolution easier, implicit conversions of
   * implicit values can sometimes fail to resolve. If you have all of the
   * imports described in the `Core` doc comment, as well as an implicit value
   * of type `Core` and are seeing errors related to implicits resolution, you
   * may be running into issues with implicits resolution.
   *
   * A quick fix is to make the values that the following implicits would
   * generate _explicitly_ available as implicits in the usage scope.
   */

  /** Implicitly converts an implicit instance of core to the underlying graph
    * data.
    *
    * @param core the core instance to convert
    * @return the graph data stored in [[core]]
    */
  implicit def getGraphData(implicit core: Core): GraphData = core.graph

  /** Implicitly converts an implicit instance of core to the associated storage
    * for literals.
    *
    * @param core the core instance to convert
    * @return the literal storage stored in [[core]]
    */
  implicit def getLiteralStorage(implicit core: Core): LiteralStorage =
    core.literalStorage

  /** Implicitly converts an implicit instance of core to the associated storage
    * for names.
    *
    * @param core the core instance to convert
    * @return the name storage stored in [[core]]
    */
  implicit def getNameStorage(implicit core: Core): NameStorage =
    core.nameStorage

  /** Implicitly converts an implicit instance of core to the associated storage
    * for parent links.
    *
    * @param core the core instance to convert
    * @return the parent link storage stored in [[core]]
    */
  implicit def getParentStorage(implicit core: Core): ParentStorage =
    core.parentStorage

  /** Implicitly converts an implicit instance of core to the associated storage
    * for ast data.
    *
    * @param core the core instance to convert
    * @return the ast storage stored in [[core]]
    */
  implicit def getAstStorage(implicit core: Core): AstStorage =
    core.astStorage

}
