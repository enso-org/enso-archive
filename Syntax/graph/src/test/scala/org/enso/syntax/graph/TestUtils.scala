package org.enso.syntax.graph

import mouse.boolean._

import org.enso.syntax.graph.API._
import org.enso.syntax.graph.AstOps._
import org.enso.syntax.graph.SpanTree.Pathed
import org.enso.syntax.text

import scala.reflect.ClassTag

trait TestUtils extends org.scalatest.Matchers {
  type ProgramText = String

  val mockModule: Module.Id = StateManagerMock.mainModule

  /** Runs action with DoubleRepresentation being set up with given program */
  def withDR[R](program: ProgramText)(
    action: DoubleRepresentation => R
  ): (R, StateManagerMock, NotificationSinkMock) = {
    val state    = StateManagerMock(program)
    val notifier = NotificationSinkMock()
    val result   = action(DoubleRepresentation(state, notifier))
    (result, state, notifier)
  }

  /** Checks if given action run on program yields expected return value. */
  def checkOutput[R](
    program: ProgramText,
    expectedOutput: R,
    action: DoubleRepresentation => R
  ): R = {
    val (result, _, _) = withDR(program)(action)
    result should be(expectedOutput)
    result
  }

  /** Checks that action transforms program from initial to expected text.
    * If notification is given, it is expected to be emitted.
    */
  def checkThatTransforms[R](
    initialProgram: ProgramText,
    expectedProgram: ProgramText,
    action: DoubleRepresentation => R,
    notification: Option[API.Notification] = None
  ): R = {
    val (result, state, notifier) = withDR(initialProgram)(action)
    state.ast.show should be(ParserUtils.preprocess(expectedProgram))
    notification.foreach(notifier.received.head should be(_))
    result
  }

  def checkIDs[R](
    program: ProgramText,
    action: DoubleRepresentation => R,
    getIDMap: text.AST => text.Parser.IDMap
  ): R = {
    val state  = StateManagerMock(program)
    val idMap  = getIDMap(state.ast)
    val result = action(DoubleRepresentation(state, NotificationSinkMock()))
    idMap match {
      case Nil           =>
      case a +: Nil      => state.ast.idMap should contain(a)
      case a +: b +: ids => state.ast.idMap should contain allOf (a, b, ids: _*)
    }
    result
  }

  def checkTextModifications(program: String): Unit = {
    val prefix = program.takeWhile(_ != '»').dropRight(1)
    val suffix = program.dropWhile(_ != '«').drop(2)
    val middle = program.dropWhile(_ != '»').drop(1).takeWhile(_ != '«')

    val pos  = TextPosition(prefix.length)
    val span = TextSpan(pos, TextLength(middle.length))

    def insertIDs(ast: text.AST) = ast.idMap.map {
      case ((off, len), id) =>
        val TextSpan(o, l) = TextSpan(off, len) + span
        (o.index, l.value) -> id
    }
    def eraseIDs(ast: text.AST) = ast.idMap.map {
      case ((off, len), id) =>
        val TextSpan(o, l) = TextSpan(off, len) - span
        (o.index, l.value) -> id
    }
    checkIDs(prefix + suffix, _.insertText(mockModule, pos, middle), insertIDs)
    checkThatTransforms(
      prefix + suffix,
      prefix + middle + suffix,
      _.insertText(mockModule, pos, middle),
      Some(TextAPI.Notification.Inserted(mockModule, pos, middle))
    )
    checkOutput(
      prefix + middle + suffix,
      middle,
      _.copyText(mockModule, span)
    )
    checkIDs(prefix + middle + suffix, _.eraseText(mockModule, span), eraseIDs)
    checkThatTransforms(
      prefix + middle + suffix,
      prefix + suffix,
      _.eraseText(mockModule, span),
      Some(TextAPI.Notification.Erased(mockModule, span))
    )
  }

  def expectTransformationError[E: ClassTag](
    initialProgram: ProgramText
  )(action: DoubleRepresentation => Unit): Unit = {
    an[E] should be thrownBy { withDR(initialProgram)(action) }
    ()
  }
  def checkModuleSingleNodeGraph[R](
    program: ProgramText
  )(action: API.Node.Description => R): R = {
    withDR(program) { dr =>
      val graph = dr.getGraph(Module.Graph.Location(mockModule))
      graph.nodes should have size 1
      graph.links should have size 0
      action(graph.nodes.head)
    }._1
  }
  def testSpanTreeFor[R](program: ProgramText)(action: SpanTree => R): R =
    checkModuleSingleNodeGraph(program) { node =>
      // make sure that span covers the whole expression
      val expectedSpan =
        TextSpan(TextPosition.Start, TextLength(program))
      node.expr.span shouldEqual expectedSpan

      verifyTreeIndices(node.expr)
      action(node.expr)
    }

  /** Takes a program with markdown. Checks that all marked actions are
    * available in the span tree and that no other actions are available.
    * @see [[SpanTreeMarkdown]] for markdown description.
    */
  def testSpanTreeMarkdown[R](markedProgram: ProgramText): Unit = {
    withClue(s"when testing markdown: `$markedProgram`") {
      val markdown = SpanTreeMarkdown(markedProgram)
      println(s"testing program: `${markdown.program}`")
      testSpanTreeFor(markdown.program) { root =>
        val nodes = root.toSeq()
        def collect[T <: Ordered[T]](
          pred: Pathed[WithActions[SpanTree]] => Boolean,
          value: Pathed[WithActions[SpanTree]] => T
        ): Seq[T] = nodes.flatMap(node => pred(node).option(value(node))).sorted

        val settable   = collect(_.settable, _.span)
        val erasable   = collect(_.erasable, _.span)
        val insertable = collect(_.insertable, _.begin)

        withClue("insertion points: ") {
          insertable shouldEqual markdown.expectedInsertionPoints
        }
        withClue("settableChildren points: ") {
          settable shouldEqual markdown.expectedSettableParts
        }
        withClue("erasable points: ") {
          erasable shouldEqual markdown.expectedErasableParts
        }
      }
    }
  }

  def expectImports(
    value: Seq[Module.Name],
    expected: Module.Name*
  ): Unit = {
    if (value.size != expected.size)
      fail(
        s"Imports list $value has ${value.size} elements"
        + s" while expected ${expected.size}"
      )

    value.zip(expected).foreach {
      case (lhs, rhs) => lhs.nameAsString() shouldEqual rhs.nameAsString()
    }
  }

  /** Traverses SpanTree and checks that text obtained by taking node span's
    * part of node text expressions is the same as pretty-printing of the node.
    *
    * @param tree A root node of the span tree.
    */
  def verifyTreeIndices(tree: SpanTree): Unit = {
    def go(node: SpanTree): Unit = {
      val textFromSpan = tree.text.substring(node.span)
      textFromSpan shouldEqual node.text
      node match {
        case node: SpanTree.Ast =>
          textFromSpan shouldEqual node.ast.show()
        case node: SpanTree.Empty =>
          node.span.length shouldEqual TextLength.Empty
        case _ =>
          Unit
      }
      node.children.foreach(go)
    }
    go(tree)
  }

  def verifyTreeIndices(node: Node.Description): Unit =
    verifyTreeIndices(node.expr)

  def asExpected[Target: ClassTag](value: AnyRef): Target = {
    value shouldBe a[Target]
    value.asInstanceOf[Target]
  }
}
