package org.enso.interpreter.test.transform

import org.enso.compiler.ir.HLIR.{Application, Binding, Block, Comment, Error, Identifier, Literal, Module}
import org.enso.interpreter.test.CompilerTest

class ASTToHLIRTest extends CompilerTest {
  val work = "translate properly"

  "Number literals" should work in {
    val code =
      """
        |1
        |""".stripMargin

    val result = Module(List(Literal.Number("1", None)))

    translate(code) shouldEqual result
  }

  "Raw string literals" should work in {
    val code =
      """
        |"Foo bar baz `` quux"
        |""".stripMargin

    val result = Module(
      List(
        Literal.Text.Raw(
          List(
            Literal.Text.Line(
              List(
                Literal.Text.Segment.Plain("Foo bar baz `` quux")
              )
            )
          )
        )
      )
    )

    translate(code) shouldEqual result
  }

  "Format string literals" should work in {
    val code =
      """
        |'a: `a` b'
        |""".stripMargin

    val result = Module(
      List(
        Literal.Text.Format(
          List(
            Literal.Text.Line(
              List(
                Literal.Text.Segment.Plain("a: "),
                Literal.Text.Segment.Expression(
                  Some(
                    Identifier.Variable("a")
                  )
                ),
                Literal.Text.Segment.Plain(" b")
              )
            )
          )
        )
      )
    )

    translate(code) shouldEqual result
  }

  "Unclosed string literals" should work in {
    val code =
      """
        |"a b c
        |""".stripMargin

    val result = Module(
      List(
        Error.UnclosedText(
          List(
            Literal.Text.Line(
              List(Literal.Text.Segment.Plain("a b c"))
            ),
            Literal.Text.Line(List())
          )
        )
      )
    )

    translate(code) shouldEqual result
  }

  // TODO [AA] Tests for translation of multiline string literals once #250 is
  //  fixed.

  "Blank identifiers" should work in {
    val code =
      """
        |_
        |""".stripMargin

    val result = Module(List(Identifier.Blank()))

    translate(code) shouldEqual result
  }

  "Variable identifiers" should work in {
    val code =
      """
        |a
        |""".stripMargin

    val result = Module(List(Identifier.Variable("a")))

    translate(code) shouldEqual result
  }

  "Constructor identifiers" should work in {
    val code =
      """
        |List
        |""".stripMargin

    val result = Module(List(Identifier.Constructor("List")))

    translate(code) shouldEqual result
  }

  "Imports" should work in {
    val code =
      """
        |import Foo.Bar.Baz
        |""".stripMargin

    val result = Module(
      List(
        Binding.Import(
          List(
            Identifier.Constructor("Foo"),
            Identifier.Constructor("Bar"),
            Identifier.Constructor("Baz")
          )
        )
      )
    )

    translate(code) shouldEqual result
  }

  "Simple type definitions" should work in {
    val code =
      """
        |type Foo a b
        |""".stripMargin

    val result = Module(
      List(
        Binding.RawType(
          Identifier.Constructor("Foo"),
          List(
            Identifier.Variable("a"),
            Identifier.Variable("b")
          ),
          None
        )
      )
    )

    translate(code) shouldEqual result
  }

  "Complex type definitions" should work in {
    val code =
      """
        |type Foo a b
        |  a : a
        |  b : b
        |
        |  add x y = x + y
        |""".stripMargin

    val result = Module(
      List(
        Binding.RawType(
          Identifier.Constructor("Foo"),
          List(
            Identifier.Variable("a"),
            Identifier.Variable("b")
          ),
          Some(
            Block.Enso(
              List(
                Application.Infix(
                  Identifier.Variable("a"),
                  Identifier.Operator(":"),
                  Identifier.Variable("a")
                ),
                Application.Infix(
                  Identifier.Variable("b"),
                  Identifier.Operator(":"),
                  Identifier.Variable("b")
                ),
                Application.Infix(
                  Application.Prefix(
                    Application.Prefix(
                      Identifier.Variable("add"),
                      Identifier.Variable("x")
                    ),
                    Identifier.Variable("y")
                  ),
                  Identifier.Operator("="),
                  Application.Infix(
                    Identifier.Variable("x"),
                    Identifier.Operator("+"),
                    Identifier.Variable("y")
                  )
                )
              )
            )
          )
        )
      )
    )

    translate(code) shouldEqual result
  }

  "Prefix applications" should work in {
    val code =
      """
        |a b
        |""".stripMargin

    val result = Module(
      List(
        Application.Prefix(Identifier.Variable("a"), Identifier.Variable("b"))
      )
    )

    translate(code) shouldEqual result
  }

  "Infix applications" should work in {
    val code =
      """
        |a + b
        |""".stripMargin

    val result = Module(
      List(
        Application.Infix(
          Identifier.Variable("a"),
          Identifier.Operator("+"),
          Identifier.Variable("b")
        )
      )
    )

    translate(code) shouldEqual result
  }

  "Mixfix applications" should work in {
    val code =
      """
        |if a then b else c
        |""".stripMargin

    val result = Module(
      List(
        Application.Mixfix(
          List(
            Identifier.Variable("if"),
            Identifier.Variable("then"),
            Identifier.Variable("else")
          ),
          List(
            Identifier.Variable("a"),
            Identifier.Variable("b"),
            Identifier.Variable("c")
          )
        )
      )
    )

    translate(code) shouldEqual result
  }

  "Left sections" should work in {
    val code =
      """
        |(1 +)
        |""".stripMargin

    val result = Module(
      List(
        Application.Section.Left(
          Literal.Number("1", None),
          Identifier.Operator("+")
        )
      )
    )

    translate(code) shouldEqual result
  }

  "Right sections" should work in {
    val code =
      """
        |(+ 1)
        |""".stripMargin

    val result = Module(
      List(
        Application.Section.Right(
          Identifier.Operator("+"),
          Literal.Number("1", None)
        )
      )
    )

    translate(code) shouldEqual result
  }

  "Side sections" should work in {
    val code =
      """
        |(+)
        |""".stripMargin

    val result = Module(
      List(
        Application.Section.Sides(
          Identifier.Operator("+")
        )
      )
    )

    translate(code) shouldEqual result
  }

  "Groups" should "disappear" in {
    val code =
      """
        |(a b)
        |""".stripMargin

    val result = Module(
      List(
        Application.Prefix(Identifier.Variable("a"), Identifier.Variable("b"))
      )
    )

    translate(code) shouldEqual result
  }

  "Enso blocks" should work in {
    val code =
      """
        |task =
        |  print 1
        |  print 2
        |""".stripMargin

    val result = Module(
      List(
        Application.Infix(
          Identifier.Variable("task"),
          Identifier.Operator("="),
          Block.Enso(
            List(
              Application.Prefix(
                Identifier.Variable("print"),
                Literal.Number("1", None)
              ),
              Application.Prefix(
                Identifier.Variable("print"),
                Literal.Number("2", None)
              )
            )
          )
        )
      )
    )

    translate(code) shouldEqual result
  }

  // TODO [AA] Test for foreign blocks once #249 is fixed

  "Unrecognised Symbols" should work in {
    val code = "@"
    val result = Module(List(Error.UnrecognisedSymbol("@")))

    translate(code) shouldEqual result
  }

  "Empty Groups" should work in {
    val code = "()"
    val result = Module(List(Error.EmptyGroup()))

    translate(code) shouldEqual result
  }

  "Documentation Comments" should work in {
    val code =
      """
        |## Some documentation
        |a + b
        |""".stripMargin

    val result = Module(List(
      Comment(List( " Some documentation")),
      Application.Infix(
        Identifier.Variable("a"),
        Identifier.Operator("+"),
        Identifier.Variable("b")
      )
    ))

    translate(code) shouldEqual result
  }
}
