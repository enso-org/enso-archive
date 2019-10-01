package org.enso.syntax.graph

import org.enso.syntax.graph
import org.enso.syntax.graph.AstOps._
import org.enso.syntax.graph.SpanTree.Node.AstLeaf
import org.enso.syntax.text.AST
import org.enso.syntax.text.AST.Opr
import org.enso.syntax.text.ast.meta.Pattern
import org.enso.syntax.text.ast.opr.Assoc

/** A GUI-friendly structure describing expression with nodes mapped to
  * expression text spans.
  *
  * SpanTree flattens nested applications into flat children lists and describes
  * possible actions, i.e. which nodes can be replaced when creating a
  * connection or where a new variable can be inserted. It also handles macros,
  * describing how its invocation can be edited.
  *
  *
  * It is expected that this structure facilitates, with possible help of
  * additional layers, the following GUI parts:
  *  - expression editor and its behavior when creating a connection
  *  - node inputs
  *  - node outputs
  *
  * This structure can be considered a layer over AST that does not introduce
  * any new data, it just presents AST in more accessible form.
  */
sealed trait SpanTree {

  import SpanTree._

  /** The text of expression part being described by this node. May be empty, if
    * this node represents just a point in text.
    */
  def text: String

  /** The span of expression part being described by this node. Indices are
    * relative to the beginning of the expression described by the tree root.
    *
    * For some nodes, like [[SpanTree.Empty]] span can be of zero length — in
    * such case the node represents a point within a string.
    */
  def span: TextSpan

  /** Left-to-right ordered sequence of children with available actions. */
  def describeChildren: Seq[WithActions[SpanTree]]

  def children: Seq[SpanTree] = describeChildren.map(_.elem)

  /** Index of the first character in the [[span]]. */
  def begin: TextPosition = span.begin

  /** Index of the first character after the [[span]] */
  def end: TextPosition = span.end

  /** Get node by traversing given path. */
  def get(path: Path): Option[SpanTree] = path match {
    case Seq() => Some(this)
    case topIndex :: tailIndices =>
      val tryChild = children.lift(topIndex)
      tryChild.flatMap(_.get(tailIndices))
  }

  /** Calls function `f` for all nodes in the tree. */
  def foreach[U](f: Pathed[WithActions[SpanTree]] => U): Unit = {
    def go(node: SpanTree, pathSoFar: Path): Unit =
      node.describeChildren.zipWithIndex.foreach {
        case (childInfo, index) =>
          val childPath = pathSoFar :+ index
          f(Pathed(childInfo, childPath))
          go(childInfo, childPath)
      }

    f(Pathed(WithActions(this, Actions.Root), RootPath))
    go(this, RootPath)
  }

  def foldLeft[B](z: B)(op: (B, Pathed[WithActions[SpanTree]]) => B): B = {
    var result = z
    foreach(nodeInfo => result = op(result, nodeInfo))
    result
  }

  def toSeq(): Seq[Pathed[WithActions[SpanTree]]] =
    foldLeft(Seq[Pathed[WithActions[SpanTree]]]()) { (acc, node) =>
      node +: acc
    }
}

object SpanTree {
  /////////////////////////////////////
  // Node subtypes aliases ////////////
  /////////////////////////////////////
  type Empty        = Node.Empty
  type Ast          = Node.Ast
  type App          = Node.App
  type OprChain     = Node.OprChain
  type AppChain     = Node.AppChain
  type Leaf         = Node.Leaf
  type AstLeaf      = Node.AstLeaf
  type MacroMatch   = Node.MacroMatch
  type MacroSegment = Node.MacroSegment

  ///////////////////////////////////////
  // Node subtypes definitions //////////
  ///////////////////////////////////////
  object Node {

    /** Base for leaves. */
    sealed trait Leaf {
      this -> SpanTree
      final def describeChildren: Seq[WithActions[SpanTree]] = Seq()
    }

    /** Node denoting a location that could contain some value but currently is
      * empty. Its span has length zero and it is not paired with any
      * AST. Typically it supports only [[Action.Insert]].
      *
      * E.g. `+` is an operator with two empty endpoints.
      */
    final case class Empty(position: TextPosition) extends SpanTree with Leaf {
      override def text: String   = ""
      override def span: TextSpan = TextSpan.Empty(position)
    }

    /** Node describing certain AST subtree, has non-zero span. */
    sealed trait Ast extends SpanTree {
      def info: Positioned[AST]
      def ast: AST       = info.elem
      def text: String   = ast.show()
      def span: TextSpan = TextSpan(info.position, TextLength(ast))
    }

    /** Includes prefix applications and generalized infix applications, like:
      * `foo bar`, `a + b`, `1,2,3`, `+`
      */
    sealed trait App extends Ast

    /** E.g. `a + b + c` flattened to a single 5-child node. Operands can be
      * set and erased. New operands can be inserted next to existing operands.
      *
      * @param opr The infix operator on which we apply operands. If there are
      *            multiple operators in chain, any of them might be referenced.
      * @param self Left- or right-most operand, depending on opr's
      *             associativity.
      * @param parts Subsequent pairs (operator, operand), beginning with the
      *              ones near-most to self operand.
      */
    final case class OprChain(
      info: Positioned[AST],
      opr: Opr,
      self: SpanTree,
      parts: Seq[OprChain.Elem]
    ) extends App {

      private def describeOperand(operand: SpanTree): WithActions[SpanTree] =
        operand match {
          case Empty(_) => WithActions(operand, Action.Insert)
          case _        => WithActions(operand, Actions.All)
        }

      override def describeChildren: Seq[WithActions[SpanTree]] = {
        val selfOperandInfo = describeOperand(self)
        var childrenInfos = parts.foldLeft(Seq(selfOperandInfo)) {
          case (acc, part) =>
            val operatorInfo = WithActions(part.operator, Actions.Function)
            val operandInfo  = describeOperand(part.operand)
            acc :+ operatorInfo :+ operandInfo
        }

        // to make children left-to-right
        if (Assoc.of(opr.name) == Assoc.Right)
          childrenInfos = childrenInfos.reverse

        // If we already miss last the argument, don't add additional
        // placeholder.
        val insertAfterLast = childrenInfos.lastOption.map(_.elem) match {
          case Some(Empty(_)) => None
          case _              => Some(WithActions.insertionPoint(end))
        }

        childrenInfos ++ insertAfterLast
      }
    }
    object OprChain {
      case class Elem(operator: AstLeaf, operand: SpanTree)
    }

    /** E.g. `foo bar baz`.
      *
      * @param callee In the example: foo
      * @param arguments In the example: Seq(bar, baz)
      */
    final case class AppChain(
      info: Positioned[AST],
      callee: SpanTree,
      arguments: Seq[SpanTree]
    ) extends App {
      def describeChildren: Seq[WithActions[SpanTree]] = {
        val calleeInfo           = WithActions(callee, Actions.Function)
        val argumentsInfo        = arguments.map(WithActions.withAll)
        val potentialNewArgument = WithActions.insertionPoint(end)
        calleeInfo +: argumentsInfo :+ potentialNewArgument
      }
    }

    /** A leaf representing an AST element that Span Tree does not decompose
      * any further.
      */
    final case class AstLeaf(info: Positioned[AST]) extends Ast with Leaf
    object AstLeaf {
      def apply(position: TextPosition, ast: AST): SpanTree.AstLeaf =
        AstLeaf(Positioned(ast, position))
    }

    /** Node representing a macro usage. */
    final case class MacroMatch(
      info: Positioned[AST],
      prefix: Option[MacroSegment],
      segments: Seq[MacroSegment]
    ) extends Ast {
      override def describeChildren: Seq[WithActions[SpanTree]] = {
        val prefixChild     = prefix.map(WithActions.withNone)
        val segmentChildren = segments.map(WithActions.withNone)
        prefixChild.toSeq ++ segmentChildren
      }
    }

    /** Node representing a macro prefix or segment. [[introducer]] is empty iff
      * represented node was prefix. Otherwise, it contains segment's head name.
      */
    final case class MacroSegment(
      override val begin: TextPosition,
      introducer: Option[String],
      patternMatch: Pattern.Match,
      override val describeChildren: Seq[WithActions[SpanTree]]
    ) extends SpanTree {
      override def span: TextSpan = {
        val introLength = TextLength(introducer.map(_.length).getOrElse(0))
        TextSpan(begin, introLength + TextLength(patternMatch))
      }
      override def text: String =
        patternMatch.toStream.foldLeft(introducer.getOrElse(""))(
          (s, ast) => s + (" " * ast.off) + ast.el.show()
        )
    }
  }

  //////////////
  //// Path ////
  //////////////

  /** An index in the sequence returned by [[SpanTree.children]] method. */
  type ChildIndex = Int

  /** A sequence of indices, describes a path through a [[SpanTree]].
    * @see [[ChildIndex]]
    */
  type Path = Seq[ChildIndex]

  /** [[Path]] to the root of the [[SpanTree]], i.e. empty path. */
  val RootPath: Path = Seq()

  ////////////////
  //// Pathed ////
  ////////////////

  case class Pathed[+T](elem: T, path: Path)
  object Pathed {
    implicit def unwrap[T](t: Pathed[T]): T                         = t.elem
    implicit def unwrapWithActions[T](t: Pathed[WithActions[T]]): T = t.elem
  }

  //////////////////////////
  //// Building Span Tree //
  //////////////////////////

  def apply(s: AST.Macro.Match.Segment, pos: TextPosition): MacroSegment = {
    val bodyPos  = pos + TextLength(s.head)
    var children = patternStructure(s.body, bodyPos)
    if (s.body.pat.matchesEmpty)
      children = children.map { child =>
        child.copy(actions = child.actions + Action.Erase)
      }
    Node.MacroSegment(pos, Some(s.head.show()), s.body, children)
  }

  def apply(last: Positioned[AST]): SpanTree =
    SpanTree(last.elem, last.position)

  def apply(ast: AST, pos: TextPosition): SpanTree = ast match {
    case AST.Opr.any(opr)          => Node.AstLeaf(pos, opr)
    case AST.Blank.any(_)          => Node.AstLeaf(pos, ast)
    case AST.Literal.Number.any(_) => Node.AstLeaf(pos, ast)
    case AST.Var.any(_)            => Node.AstLeaf(pos, ast)
    case AST.App.Prefix.any(app) =>
      val info          = Positioned(ast, pos)
      val childrenAsts  = ast.flattenPrefix(pos, app)
      val childrenNodes = childrenAsts.map(SpanTree(_))
      childrenNodes match {
        case callee :: args => Node.AppChain(info, callee, args)
        case _              =>
          // app prefix always has two children, flattening can only add more
          throw new Exception("impossible: failed to find application target")
      }

    case m @ AST.Macro.Match(optPrefix, segments, resolved @ _) =>
      val optPrefixNode = optPrefix.map { m =>
        val children = patternStructure(m, pos)
        Node.MacroSegment(pos, None, m, children)
      }
      var i = pos + optPrefixNode
          .map(_.span.length)
          .getOrElse(TextLength.Empty)

      val segmentNodes = segments.toList().map { s =>
        val node = SpanTree(s.el, i + s.off)
        i += node.span.length + TextLength(s.off)
        node
      }

      Node.MacroMatch(Positioned(m, pos), optPrefixNode, segmentNodes)

    case _ =>
      GeneralizedInfix(ast) match {
        case Some(info) =>
          def toNode(operand: GeneralizedInfix.Operand): SpanTree =
            operand.elem match {
              case Some(operandAST) =>
                SpanTree(operandAST, operand.position)
              case None =>
                Node.Empty(operand.position)
            }

          val nodeInfo = Positioned(ast, pos)
          val chain    = info.flattenInfix(pos)
          val selfNode = toNode(chain.self)
          val parts = chain.parts.map { part =>
            val operatorNode = AstLeaf(part.operator)
            val operandNode  = toNode(part.operand)
            Node.OprChain.Elem(operatorNode, operandNode)
          }
          Node.OprChain(nodeInfo, info.oprAST, selfNode, parts)
        case _ =>
          throw new NotImplementedError(
            s"internal error: not supported ast: $ast"
          )
      }
  }

  /** Sequence of nodes with their possible actions for a macro pattern. */
  def patternStructure(
    patMatch: Pattern.Match,
    pos: TextPosition
  ): Seq[WithActions[SpanTree]] = patMatch match {
    case Pattern.Match.Or(_, elem) =>
      patternStructure(elem.fold(identity, identity), pos)
    case Pattern.Match.Seq(_, elems) =>
      val left       = patternStructure(elems._1, pos)
      val leftLength = TextLength(elems._1)
      val right      = patternStructure(elems._2, pos + leftLength)
      left ++ right
    case Pattern.Match.Build(_, elem) =>
      val node = SpanTree(elem.el, pos + elem.off)
      Seq(WithActions(node, Action.Set))
    case Pattern.Match.End(_) =>
      Seq()
    case Pattern.Match.Nothing(_) =>
      Seq()
    case _ =>
      throw new NotImplementedError(
        s"internal error: not supported pattern: $patMatch"
      )
  }
}
