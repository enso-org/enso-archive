package org.enso.syntax.graph

import org.enso.syntax.graph.API._
import org.scalatest._

import scala.reflect.ClassTag

class Tests extends FunSuite with TestUtils {
  test("text modifications") {
    List(
      "a (»«) c",
      "+(»  «)+",
      "a(»xx«)c",
      "a(»  «)c",
      "a (»1 2«) c",
      "'text(»'«)",
      "if a then b(» then x«) else c",
      "((( a (»+ b«) )))"
    ).foreach(checkTextModifications)
  }

  test("recognizing lack of imports") {
    withDR("") { dr =>
      val imports = dr.importedModules(mockModule)
      imports should have length 0
    }
  }
  test("recognizing single import") {
    withDR("import Foo.Baz") { dr =>
      val imports = dr.importedModules(mockModule)
      expectImports(imports, Module.Name("Foo.Baz"))
    }
  }
  test("adding first import") {
    checkThatTransforms(
      "",
      "import Foo.Baz",
      _.importModule(mockModule, Module.Name("Foo.Baz"))
    )
  }
  test("adding second import") {
    checkThatTransforms(
      "import Foo.Bar",
      "import Foo.Bar\nimport Foo.Baz",
      _.importModule(mockModule, Module.Name("Foo.Baz"))
    )
  }
  test("adding import when there is empty line and definition") {
    checkThatTransforms(
      """import Foo
        |
        |import Foo.Bar
        |
        |add x y = x + y""".stripMargin,
      """import Foo
        |
        |import Foo.Bar
        |import Foo.Baz
        |
        |add x y = x + y""".stripMargin,
      _.importModule(mockModule, Module.Name("Foo.Baz"))
    )
  }
  test("adding duplicated import") {
    expectTransformationError[API.ImportAlreadyExistsException](
      "import Foo.Bar"
    ) {
      _.importModule(mockModule, Module.Name("Foo.Bar"))
    }
  }
  test("removing the only import") {
    checkThatTransforms(
      "import Foo.Baz",
      "",
      _.removeImport(mockModule, Module.Name("Foo.Baz"))
    )
  }
  test("removing one of several imports") {
    checkThatTransforms(
      """import Foo
        |import Foo.Baz
        |import Foo.Bar
        |add x y = x + y""".stripMargin,
      """import Foo
        |import Foo.Bar
        |add x y = x + y""".stripMargin,
      _.removeImport(mockModule, Module.Name("Foo.Baz"))
    )
  }
  test("removing import between empty lines") {
    checkThatTransforms(
      """import Foo
        |
        |import Foo.Baz
        |
        |add x y = x + y""".stripMargin,
      """import Foo
        |
        |
        |add x y = x + y""".stripMargin,
      _.removeImport(mockModule, Module.Name("Foo.Baz"))
    )
  }
  test("get empty module graph") {
    withDR("") { dr =>
      val graph = dr.getGraph(Module.Graph.Location(mockModule))
      graph.nodes should have size 0
      graph.links should have size 0
    }
  }
  test("no nodes from function def") {
    withDR("add a b = a + b") { dr =>
      val graph = dr.getGraph(Module.Graph.Location(mockModule))
      graph.nodes should have size 0
      graph.links should have size 0
    }
  }
  test("no nodes from import") {
    withDR("import Foo.Bar") { dr =>
      val graph = dr.getGraph(Module.Graph.Location(mockModule))
      graph.nodes should have size 0
      graph.links should have size 0
    }
  }
//  test("no nodes from operator def") {
//    withDR(
//      "+ a b = a + b",
//      dr => {
//        val graph = dr.getGraph(Module.Graph.Location(mockModule))
//        graph.nodes should have size 0
//        graph.links should have size 0
//      }
//    )
//  }
  test("node trivial literal") {
    checkModuleSingleNodeGraph("15") { node =>
      node.expr.text shouldEqual "15"
      node.outputs shouldEqual None
    }
  }
  test("atom in in brackets") {
    case class Case(literal: String, loff: Int, roff: Int) {
      val leftSegment  = "(" + " " * loff + literal
      val rightSegment = ")"
      val program      = leftSegment + " " * roff + ")"
    }

    val cases = Seq(
      Case("15", 0, 0),
      Case("foo", 1, 1),
      Case("15", 2, 0),
      Case("_", 0, 0),
      Case("15", 0, 2)
    )

    cases.foreach { testedCase =>
      testSpanTreeFor(testedCase.program) { tree =>
        val children = asExpected[SpanTree.MacroMatch](tree).describeChildren
        children should have size 2
        children match {
          case Seq(leftInfo, rightInfo) =>
            val rightSegment =
              asExpected[SpanTree.MacroSegment](rightInfo.elem)
            val leftSegment =
              asExpected[SpanTree.MacroSegment](leftInfo.elem)

            leftSegment.text shouldEqual testedCase.leftSegment
            leftInfo.actions shouldEqual Set()

            val leftChildren = leftSegment.describeChildren
            leftChildren should have size 1
            val bracketContents = leftChildren.head
            val leftChildNode =
              asExpected[SpanTree.AstLeaf](bracketContents.elem)
            leftChildNode.text shouldEqual testedCase.literal
            bracketContents.actions shouldEqual Set(Action.Set, Action.Erase)

            rightSegment.text shouldEqual testedCase.rightSegment
            rightInfo.actions shouldEqual Set()
        }
      }
    }
  }
  test("span tree: skip expr") {
    val expr    = "15 +  foo"
    val program = s"skip  $expr"
    testSpanTreeFor(program) { tree =>
      tree shouldBe a[SpanTree.MacroMatch]
      val segment = expectChild[SpanTree.MacroSegment](tree, Set())
      segment.text shouldEqual program

      val exprChild =
        expectChild[SpanTree.OprChain](segment, Set(Action.Set))
      exprChild.text shouldEqual expr
    }
  }

  def expectChild[R <: SpanTree: ClassTag](
    spanTree: SpanTree,
    expectedActions: Set[Action]
  ): R =
    expectChildren(spanTree, Seq(expectedActions)) match {
      case Seq(child) =>
        asExpected[R](child)
    }

  def expectChildren2[R1 <: SpanTree: ClassTag, R2 <: SpanTree: ClassTag](
    spanTree: SpanTree,
    expectedActions1: Set[Action],
    expectedActions2: Set[Action]
  ): (R1, R2) = {
    expectChildren(spanTree, Seq(expectedActions1, expectedActions2)) match {
      case Seq(child1, child2) =>
        (asExpected[R1](child1), asExpected[R2](child2))
    }
  }

  def expectChildren(
    spanTree: SpanTree,
    expectedActionsPerChild: Seq[Set[Action]]
  ): Seq[SpanTree] = {
    val children = spanTree.describeChildren
    children should have size expectedActionsPerChild.size

    children.zip(expectedActionsPerChild).map {
      case (child, expectedActions) =>
        child.actions shouldEqual expectedActions
        child.elem
    }
  }
  test("span tree: if foo bar baz then   a+b") {
    val conditionText = "foo bar baz"
    val thenExpr      = "a+b"
    val program       = s"if $conditionText then   $thenExpr"
    testSpanTreeFor(program) { tree =>
      tree shouldBe a[SpanTree.MacroMatch]
      val macroChildren =
        expectChildren2[SpanTree.MacroSegment, SpanTree.MacroSegment](
          tree,
          Set(),
          Set()
        )

      val ifSegment = macroChildren._1
      ifSegment.text shouldEqual s"if $conditionText"
      val ifExprChild = expectChild[SpanTree.AppChain](
        ifSegment,
        Set(Action.Set)
      )
      ifExprChild.text shouldEqual conditionText

      val thenSegment = asExpected[SpanTree.MacroSegment](macroChildren._2)
      thenSegment.text shouldEqual s"then   $thenExpr"
      val thenExprChild = expectChild[SpanTree.OprChain](
        thenSegment,
        Set(Action.Set)
      )
      thenExprChild.text shouldEqual thenExpr
    }
  }
  test("node trivial var") {
    checkModuleSingleNodeGraph("foo") { node =>
      node.expr.text should equal("foo")
    }
  }
//  test("node single paren arg app") {
//    checkModuleSingleNodeGraph("foo (4)") { node =>
//      node.expr.text should equal("foo (4)")
//      node.inputs should have size 1
//      node.inputs should equal(Seq(Port.Empty))
//      node.flags shouldBe empty
//    }
//  }
//  test("node two arg app with paren") {
//    checkModuleSingleNodeGraph("(foo a) _") { node =>
//      node.expr.text should equal("(foo a) _")
//      node.inputs should equal(Port.Empty(2))
//      node.flags shouldBe empty
//    }
//  }
  test("get trivial named node") {
    checkModuleSingleNodeGraph("a = 15") { node =>
      node.expr.text should equal("15")
    }
  }

  test("prefix application actions") {
    testSpanTreeMarkdown("foo") // TODO should `foo` be settable?
    testSpanTreeMarkdown("‹foo› ⎀«a»⎀")
    testSpanTreeMarkdown("‹foo› ⎀«4»⎀")
    testSpanTreeMarkdown("‹foo› ⎀«a» ⎀«_»⎀")
    // can insert before each argument, after `b`, after `foo a b`
  }

  test("infix operator chains") {
    // test programs that are independent whether operator is left- or
    // right-associative
    def generalCases(opr: String) =
      Seq(
        "⎀‹+›⎀",
        "⎀«a»‹+›⎀",
        "⎀‹+›⎀«b»⎀",
        "⎀‹+›⎀«5»⎀",
        "⎀‹+› ⎀«5»⎀",
        "⎀‹+›⎀«b»‹+›⎀",
        "⎀‹+›⎀«b»‹+›⎀«c»⎀",
        "⎀«a»‹+›⎀«b»‹+›⎀",
        "⎀«a»‹+›⎀«b»⎀",
        "⎀‹+›⎀«a»‹+›⎀«b»‹+›⎀"
      ).map(_.replace("+", opr))

    val leftAssocCases = Seq("⎀‹+›⎀ ‹+›⎀") ++ generalCases("+")
    leftAssocCases.foreach(testSpanTreeMarkdown)

    val rightAssocCases = Seq("⎀‹,› ⎀‹,›⎀") ++ generalCases(",")
    rightAssocCases.foreach(testSpanTreeMarkdown)
  }

  test("arrow macro") {
    // not like other operators, as it is a macro
    testSpanTreeMarkdown("‹a›->‹b›")
  }

  test("mixed infix and app") {
    // three insertion points for a+b and two before/after `foo` argument.
    testSpanTreeMarkdown("‹⎀«a»‹+›⎀«b»⎀› ⎀«foo»⎀")
    testSpanTreeMarkdown("‹foo› ⎀«⎀«a»‹+›⎀«b»⎀»⎀")
  }

  test("flattening prefix application") {
    testSpanTreeFor("a b c 4") { root =>
      root.children match {
        case Seq(a_, b, c, d, insertPoint) =>
          a_.text shouldEqual "a"
          b.text shouldEqual "b"
          c.text shouldEqual "c"
          d.text shouldEqual "4"
          insertPoint shouldBe a[SpanTree.Empty]
        case _ =>
          fail(s"wrong element count in: ${root.children}")
      }
      root.children.foreach(_.children should have size 0)
    }
  }
  test("flattening infix chain") {
    testSpanTreeFor("a+b+c+4") { root =>
      root.children match {
        case Seq(a_, opr, b, opr2, c, opr3, d, insertPoint) =>
          a_.text shouldEqual "a"
          opr.text shouldEqual "+"
          b.text shouldEqual "b"
          opr2.text shouldEqual "+"
          c.text shouldEqual "c"
          opr3.text shouldEqual "+"
          d.text shouldEqual "4"
          insertPoint shouldBe a[SpanTree.Empty]
        case _ =>
          fail(s"wrong element count in: ${root.children}")
      }
      root.children.foreach(_.children should have size 0)
    }
  }
  test("flattening right-associative infix chain") {
    testSpanTreeFor("a , b , c , 4") { root =>
      root.children match {
        case Seq(a_, opr, b, opr2, c, opr3, d, insertPoint) =>
          a_.text shouldEqual "a"
          opr.text shouldEqual ","
          b.text shouldEqual "b"
          opr2.text shouldEqual ","
          c.text shouldEqual "c"
          opr3.text shouldEqual ","
          d.text shouldEqual "4"
          insertPoint shouldBe a[SpanTree.Empty]
        case _ =>
          fail(s"wrong element count in: ${root.children}")
      }
      root.children.foreach(_.children should have size 0)
    }
  }
  test("flattening infix application2") {
    testSpanTreeFor("a +  b *  c + d") { root =>
      root.children match {
        case Seq(a_, opr, bTimesC, opr2, d, insertPoint) =>
          a_.text shouldEqual "a"
          opr.text shouldEqual "+"
          bTimesC.text shouldEqual "b *  c"
          opr2.text shouldEqual "+"
          d.text shouldEqual "d"
          insertPoint shouldBe a[SpanTree.Empty]

          bTimesC shouldBe a[SpanTree.OprChain]
          bTimesC
            .asInstanceOf[SpanTree.OprChain]
            .opr
            .name shouldEqual "*"
          bTimesC.children match {
            case Seq(b, times, c, nestedInsertPoint) =>
              b shouldBe a[SpanTree.AstLeaf]
              b.text shouldEqual "b"
              times shouldBe a[SpanTree.AstLeaf]
              times.text shouldEqual "*"
              b shouldBe a[SpanTree.AstLeaf]
              c.text shouldEqual "c"
              insertPoint shouldBe a[SpanTree.Empty]
              nestedInsertPoint shouldBe a[SpanTree.Empty]
            case _ =>
              fail(s"wrong children count for ${bTimesC.text}")
          }

        case _ =>
          fail(s"wrong element count in: ${root.children}")
      }
    }
  }
}
