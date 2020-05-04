package org.enso.compiler.test.codegen

import org.enso.compiler.core.IR
import org.enso.compiler.test.CompilerTest

class AstToIRTest extends CompilerTest {

  "AST translation of lambda definitions" should {
    "result in a syntax error when defined with multiple arguments" in {
      val ir =
        """x y -> x + y
          |""".stripMargin.toIrExpression.get

      ir shouldBe an[IR.Error.Syntax]

      ir.asInstanceOf[IR.Error.Syntax].message shouldEqual
      "Syntax is not supported yet: pattern matching function arguments."
    }

    "support standard lambda chaining" in {
      val ir =
        """
          |x -> y -> z -> x
          |""".stripMargin.toIrExpression.get

      ir shouldBe an[IR.Function.Lambda]
      ir.asInstanceOf[IR.Function.Lambda].body shouldBe an[IR.Function.Lambda]
      ir.asInstanceOf[IR.Function.Lambda]
        .body
        .asInstanceOf[IR.Function.Lambda]
        .body shouldBe an[IR.Function.Lambda]
    }
  }

  "AST translation of operator sections" should {
    "work properly for left sections" in {
      val ir =
        """
          |(1 +)
          |""".stripMargin.toIrExpression.get

      ir shouldBe an[IR.Application.Operator.Section.Left]
    }

    "work properly for sides sections" in {
      val ir =
        """
          |(+)
          |""".stripMargin.toIrExpression.get

      ir shouldBe an[IR.Application.Operator.Section.Sides]
    }

    "work properly for right sections" in {
      val ir =
        """
          |(+ 1)
          |""".stripMargin.toIrExpression.get

      ir shouldBe an[IR.Application.Operator.Section.Right]
    }

    "disallow sections with blank arguments" in {
      val ir =
        """
          |(_ +)
          |""".stripMargin.toIrExpression.get

      ir shouldBe an[IR.Error.Syntax]
      ir.asInstanceOf[IR.Error.Syntax]
        .reason shouldBe an[IR.Error.Syntax.BlankArgInSection.type]
    }

    "disallow sections with named arguments" in {
      val ir =
        """
          |(+ (left=1))
          |""".stripMargin.toIrExpression.get

      ir shouldBe an[IR.Error.Syntax]
      ir.asInstanceOf[IR.Error.Syntax]
        .reason shouldBe an[IR.Error.Syntax.NamedArgInSection.type]
    }
  }

  "AST translation of function applications" should {
    "allow use of blank arguments" in {
      val ir =
        """
          |a b _ d
          |""".stripMargin.toIrExpression.get
          .asInstanceOf[IR.Application.Prefix]

      ir.arguments(1) shouldBe an[IR.CallArgument.Specified]
      ir.arguments(1)
        .asInstanceOf[IR.CallArgument.Specified]
        .value shouldBe an[IR.Expression.Blank]
    }

    "allow use of named blank arguments" in {
      pending
    }

    "allow method-call syntax on a blank" in {
      pending
    }
  }

  "AST translation of case expressions" should {
    "support a blank scrutinee" in {
      pending
    }
  }
}
