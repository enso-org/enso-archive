package org.enso.core

import org.enso.graph.definition.Macro.{component, field, genGraph, opaque}
import org.enso.graph.{Sized, VariantIndexed, Graph => PrimGraph}
import shapeless.{::, HNil}

// TODO [AA] More detailed semantic descriptions for each node shape in future.
object CoreGraph {
  @genGraph object Definition {

    // ========================================================================
    // === The Graph Definition ===============================================
    // ========================================================================

    /** This type denotes the core graph itself. */
    case class CoreGraph() extends PrimGraph

    /** The list of components that make up a [[CoreGraph]]. */
    implicit def components =
      new PrimGraph.Component.List[CoreGraph] {
        type Out = Nodes :: Links :: HNil
      }

    // ========================================================================
    // === Opaque Storage =====================================================
    // ========================================================================

    /** Storage for string literals. */
    @opaque case class Literal(opaque: String)

    /** Storage for name literals. */
    @opaque case class Name(opaque: String)

    /** Storage for parents for a given node.
      *
      * An entry in the vector will be the index of an [[Edge]] in the graph
      * that has the containing node as its `target` field.
      */
    @opaque case class Parent(opaque: Vector[Int])

    // ========================================================================
    // === Node ===============================================================
    // ========================================================================

    /** A node in the [[CoreGraph]]. */
    @component case class Nodes() { type Node[G <: PrimGraph] }

    /** The list of fields that a [[Node]] has in a [[CoreGraph]]. */
    implicit def nodeFields =
      new PrimGraph.Component.Field.List[CoreGraph, Nodes] {
        type Out = Node.Shape :: Node.ParentLinks :: Node.Location :: HNil
      }

    object Node {

      // ======================================================================
      // === Field Definitions ================================================
      // ======================================================================

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

        // === Base Shapes ====================================================
        /** A representation of a node that has no particular shape. */
        case class Empty()

        /** A representation of a cons cell for building linked lists on the
          * graph.
          *
          * These should be used _very_ sparingly, if at all, but they provide a
          * way to store dynamically-sized core components providing they can be
          * broken down into statically sized components.
          *
          * The [[tail]] parameter should always point to either another node
          * with shape [[List]] or a node with shape [[Nil]].
          *
          * It should be noted that, given that each [[Node]] contains a field
          * of [[ParentLinks]], that constructing this properly provides a
          * doubly-linked list, as no [[List]] or [[Nil]] should have more than
          * one parent.
          *
          * @param head the current, arbitrary, element in the list
          * @param tail the rest of the list
          */
        case class List(head: Link[G], tail: Link[G])

        /** A representation of the end of a linked-list on the graph. */
        case class Nil()

        /** A node representing boolean true. */
        case class True()

        /** A node representing boolean false. */
        case class False()

        // === Literals =======================================================

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

        /** A representation of literal text from a foreign code block.
          *
          * @param literal a link to the [[RawLiteral]] representing the code
          */
        case class ForeignCodeLiteral(literal: Link[G])

        // === Names ==========================================================

        /** A name.
          *
          * @param nameLiteral a link to the name literal, represented as a
          *                    [[NameLiteral]]
          */
        case class Name(nameLiteral: Link[G])

        /** A representation of the `this` reserved name */
        case class ThisName()

        /** A representation of the `here` reserved name */
        case class HereName()

        // === Module =========================================================

        /** The core representation of a top-level Enso module.
          *
          * @param name the name of the module
          * @param imports the module's imports as a [[List]], where each list
          *                member points to an import
          * @param definitions the module's definitions as a [[List]], where
          *                    each list member points to a binding
          */
        case class Module(name: Link[G], imports: Link[G], definitions: Link[G])

        /** An import statement.
          *
          * @param segments the segments of the import path, represented as a
          *                 [[NameLiteral]].
          */
        case class Import(segments: Link[G])

        /** A module-level binding.
          *
          * @param module a link to the module in which this binding is found
          * @param binding the binding itself
          */
        case class TopLevelBinding(module: Link[G], binding: Link[G])

        // === Type Definitions ===============================================

        /** An atom definition.
          *
          * @param name the name of the atom
          * @param args the atom's arguments as a [[List]]
          */
        case class AtomDef(name: Link[G], args: Link[G])

        /** An expanded-form type definition, with a body.
          *
          * @param name the name of the aggregate type
          * @param typeParams the type parameters to the definition
          * @param body the body of the type definition, represented as a
          *             [[List]] of bindings
          */
        case class ComplexTypeDef(
          name: Link[G],
          typeParams: Link[G],
          body: Link[G]
        )

        // === Typing =========================================================

        /** A type signature.
          *
          * @param typed the expression being ascribed a type
          * @param sig the signature being ascribed to [[typed]]
          */
        case class TypeAscription(typed: Link[G], sig: Link[G])

        /** The `in` portion of a type signature that represents the monadic
          * contexts.
          *
          * @param typed the type being put in a context
          * @param context the context
          */
        case class ContextAscription(typed: Link[G], context: Link[G])

        /** A representation of a typeset member.
          *
          * PLEASE NOTE: This is here more as a note than anything, and will not
          * be exposed to users yet. It is currently used for Atom arguments.
          *
          * @param label the member's label, if given
          * @param memberType the member's type, if given
          * @param value the member's value, if given
          */
        case class TypesetMember(
          label: Link[G],
          memberType: Link[G],
          value: Link[G]
        )

        /** The typset subsumption judgement `<:`.
          *
          * @param left the left type in the subsumption judgement
          * @param right the right type in the subsumption judgement
          */
        case class TypesetSubsumption(left: Link[G], right: Link[G])

        /** The typeset equality judgement `~`.
          *
          * @param left the left operand
          * @param right the right operand
          */
        case class TypesetEquality(left: Link[G], right: Link[G])

        /** The typeset concatenation operator `,`.
          *
          * @param left the left operand
          * @param right the right operand
          */
        case class TypesetConcat(left: Link[G], right: Link[G])

        /** The typeset union operator `|`.
          *
          * @param left the left operand
          * @param right the right operand
          */
        case class TypesetUnion(left: Link[G], right: Link[G])

        /** The typeset intersection operator `&`.
          *
          * @param left the left operand
          * @param right the right operand
          */
        case class TypesetIntersection(left: Link[G], right: Link[G])

        /** The typeset subtraction operator `\`.
          *
          * @param left the left operand
          * @param right the right operand
          */
        case class TypesetSubtraction(left: Link[G], right: Link[G])

        // === Function =======================================================

        /** A lambda expression, the `->` function arrrow.
          *
          * Note that all lambdas in Enso are explicitly single-argument.
          *
          * @param arg the argument to the lambda
          * @param body the body of the lambda
          */
        case class Lambda(arg: Link[G], body: Link[G])

        /** A sugared function definition.
          *
          * @param name the name of the function
          * @param args the function arguments, as a [[List]]
          * @param body the body of the function
          */
        case class FunctionDef(name: Link[G], args: Link[G], body: Link[G])

        /** A method definition.
          *
          * @param targetPath the path of the method
          * @param name the name of the method
          * @param function the function that is executed (can be any callable
          *                 representation)
          */
        case class MethodDef(
          targetPath: Link[G],
          name: Link[G],
          function: Link[G]
        )

        // === Definition-Site Argument Types =================================

        /** An ignored function argument, denoted by `_`.
          *
          * This can commonly be seen in use where an API requires a function
          * take an argument, but a particular implementation doesn't need it:
          * `_ -> ...`.
          */
        case class IgnoredArgument()

        /** A function argument definition.
          *
          * @param name the name of the argument
          * @param suspended whether or not the argument uses suspended
          *                  evaluation (should be [[True]] or [[False]]
          * @param default the default value for the argument, if present
          */
        case class DefinitionArgument(
          name: Link[G],
          suspended: Link[G],
          default: Link[G]
        )

        // === Applications ===================================================

        /** A function application.
          *
          * All functions in Enso are curried by default, and are represented in
          * the [[CoreGraph]] as single-argument functions.
          *
          * @param function function expression being called
          * @param argument the argument to the function
          */
        case class Application(function: Link[G], argument: Link[G])

        /** A mixfix function application.
          *
          * @param function the name of the mixfix function
          * @param arguments the arguments to the mixfix function as a [[List]]
          */
        case class MixfixApplication(function: Link[G], arguments: Link[G])

        /** An infix function application.
          *
          * @param left the left argument
          * @param operator the function being applied
          * @param right the right argument
          */
        case class InfixApplication(
          left: Link[G],
          operator: Link[G],
          right: Link[G]
        )

        /** A left section operator application.
          *
          * @param arg the left argument to [[operator]]
          * @param operator the function being sectioned
          */
        case class LeftSection(arg: Link[G], operator: Link[G])

        /** A right section operator application.
          *
          * @param operator the function being sectioned
          * @param arg the right argument to [[operator]]
          */
        case class RightSection(operator: Link[G], arg: Link[G])

        /** A centre section operator application.
          *
          * @param operator the operator being sectioned
          */
        case class CentreSection(operator: Link[G])

        /** A representatin of a term that is explicitly forced.
          *
          * PLEASE NOTE: This is temporary and will be removed as soon as the
          * compiler is capable enough to not require it.
          *
          * @param expression
          */
        case class ForcedTerm(expression: Link[G])

        // === Call-Site Argument Types =======================================

        /** Used to represent `_` arguments that are shorthand for the creation
          * of lambdas.
          */
        case class LambdaShorthandArgument()

        /** A function call-site argument.
          *
          * @param expression the argument expression
          * @param name the name of the argument, if given
          */
        case class CallSiteArgument(expression: Link[G], name: Link[G])

        /** The `...` argument that may be passed to a function to suspend the
          * execution of its default arguments.
          */
        case class SuspendDefaultsOperator()

        // === Structure ======================================================

        /** A block expression.
          *
          * @param expressions the expressions in the block as a [[List]]
          * @param returnVal the final expression of the block
          */
        case class Block(expressions: Link[G], returnVal: Link[G])

        /** A binding expression of the form `name = expr`.
          *
          * @param name the name being bound to
          * @param expression the expression being bound to [[name]]
          */
        case class Binding(name: Link[G], expression: Link[G])

        // === Case Expression ================================================

        /** A case expression.
          *
          * @param scrutinee the case expression's scrutinee
          * @param branches the match branches, as a [[List]]
          */
        case class CaseExpr(scrutinee: Link[G], branches: Link[G])

        /** A case branch.
          *
          * @param pattern the pattern to match the scrutinee against
          * @param expression the expression
          */
        case class CaseBranch(pattern: Link[G], expression: Link[G])

        /** A pattern that matches on the scrutinee based on its structure.
          *
          * @param matchExpression the expression representing the possible
          *                        structure of the scrutinee
          */
        case class StructuralMatch(matchExpression: Link[G])

        /** A pattern that matches on the scrutinee purely based on a type
          * subsumption judgement.
          *
          * @param matchExpression the expression representing the possible type
          *                        of the scrutinee
          */
        case class TypeMatch(matchExpression: Link[G])

        /** A pattern that matches on the scrutinee based on a type subsumption
          * judgement and assigns a new name to it for use in the branch.
          *
          * @param matchExpression the expression representing the possible type
          *                        of the scrutinee, and its new name
          */
        case class NamedMatch(matchExpression: Link[G])

        /** A pattern that matches on any scrutinee. */
        case class FallbackMatch()

        // === Comments =======================================================

        /** A documentation comment.
          *
          * @param commented the commented entity
          * @param doc a [[TextLiteral]] containing the documentation comment
          */
        case class DocComment(commented: Link[G], doc: Link[G])

        /** A disable comment.
          *
          * @param disabledExpr the portion of the program that has been
          *                     disabled
          */
        case class DisableComment(disabledExpr: Link[G])

        // === Foreign ========================================================

        /** A foreign code definition.
          *
          * @param language the name of the foreign programming language
          * @param code the foreign code, represented as a [[ForeignCodeLiteral]]
          */
        case class ForeignDefinition(language: Link[G], code: Link[G])

        // === Errors =========================================================

        /** A syntax error.
          *
          * @param errorNode the node representation of the syntax error
          */
        case class SyntaxError(errorNode: Link[G])

        // TODO [AA] Fill in the error types as they become evident
      }

      // ======================================================================
      // === Utility Functions ================================================
      // ======================================================================

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
      def isErrorNode(
        node: Node[CoreGraph]
      )(implicit graph: PrimGraph.GraphData[CoreGraph]): Boolean = {
        node match {
          case Shape.SyntaxError.any(_) => true
          case _                        => false
        }
      }

      /** Checks whether a given node represents syntactic sugar.
        *
        * @param node the node to check
        * @return `true` if [[node]] represents syntax sugar, `false` otherwise
        */
      def shapeIsSugar(
        node: Node[CoreGraph]
      )(implicit graph: PrimGraph.GraphData[CoreGraph]): Boolean = {
        node match {
          case Shape.ComplexTypeDef.any(_)    => true
          case Shape.FunctionDef.any(_)       => true
          case Shape.MixfixApplication.any(_) => true
          case Shape.InfixApplication.any(_)  => true
          case Shape.LeftSection.any(_)       => true
          case Shape.RightSection.any(_)      => true
          case Shape.CentreSection.any(_)     => true
          case Shape.ForcedTerm.any(_)        => true
          case _                              => false
        }
      }

      /** Checks whether a given node represents primitive language constructs.
        *
        * @param node the node to check
        * @return `true` if [[Node]] has a primitive shape, `false` otherwise
        */
      def shapeIsPrimitive(
        node: Node[CoreGraph]
      )(implicit graph: PrimGraph.GraphData[CoreGraph]): Boolean = {
        !shapeIsSugar(node)
      }
    }

    // ========================================================================
    // === Link ===============================================================
    // ========================================================================

    /** A link between nodes in the [[CoreGraph]]. */
    @component case class Links() { type Link[G <: PrimGraph] }

    /** The list of fields that a [[Link]] has in a [[CoreGraph]]. */
    implicit def linkFields =
      new PrimGraph.Component.Field.List[CoreGraph, Links] {
        type Out = Link.Shape :: HNil
      }

    object Link {

      // ======================================================================
      // === Field Definitions ================================================
      // ======================================================================

      /** The shape of a link is static and represents a standard directional
        * edge in a graph.
        *
        * @param source the node at the start of the link
        * @param target the node at the end of the link
        * @tparam G the graph type
        */
      @field case class Shape[G <: PrimGraph](source: Node[G], target: Node[G])

      // ======================================================================
      // === Utility Functions ================================================
      // ======================================================================
    }
  }
}
