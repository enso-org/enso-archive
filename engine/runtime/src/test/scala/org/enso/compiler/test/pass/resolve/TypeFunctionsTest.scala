package org.enso.compiler.test.pass.resolve

import org.enso.compiler.Passes
import org.enso.compiler.context.{FreshNameSupply, InlineContext}
import org.enso.compiler.core.IR
import org.enso.compiler.pass.resolve.{IgnoredBindings, TypeFunctions}
import org.enso.compiler.pass.{IRPass, PassConfiguration, PassManager}
import org.enso.compiler.test.CompilerTest

class TypeFunctionsTest extends CompilerTest {

  // === Test Setup ===========================================================

  val passes = new Passes

  val precursorPasses: List[IRPass] = passes.getPrecursors(TypeFunctions).get

  val passConfiguration: PassConfiguration = PassConfiguration()

  implicit val passManager: PassManager =
    new PassManager(precursorPasses, passConfiguration)

  /** Adds an extension method to resolve typing functions to an expression.
    *
    * @param ir the expression to resolve typing functions in
    */
  implicit class ResolveExpression(ir: IR.Expression) {

    /** Resolves typing functions on [[ir]].
      *
      * @param inlineContext the context win which resolution takes place
      * @return [[ir]], with typing functions resolved
      */
    def resolve(implicit inlineContext: InlineContext): IR.Expression = {
      TypeFunctions.runExpression(ir, inlineContext)
    }
  }

  /** Makes an inline context.
    *
    * @return a new inline context
    */
  def mkInlineContext: InlineContext = {
    InlineContext(freshNameSupply = Some(new FreshNameSupply))
  }

  // === The Tests ============================================================

  "Type functions resolution" should {
    implicit val ctx: InlineContext = mkInlineContext

    "work for saturated applications" in {
      val ir =
        """
          |a : B
          |""".stripMargin.preprocessExpression.get.resolve

      ir shouldBe an[IR.Type.Ascription]
    }

    "work for left sections" in {
      val ir =
        """
          |(a :)
          |""".stripMargin.preprocessExpression.get.resolve

      ir shouldBe an[IR.Function.Lambda]
      ir.asInstanceOf[IR.Function.Lambda].body shouldBe an[IR.Type.Ascription]
    }

    "work for centre sections" in {
      val ir =
        """
          |(:)
          |""".stripMargin.preprocessExpression.get.resolve

      ir shouldBe an[IR.Function.Lambda]
      ir.asInstanceOf[IR.Function.Lambda]
        .body
        .asInstanceOf[IR.Function.Lambda]
        .body shouldBe an[IR.Type.Ascription]
    }

    "work for right sections" in {
      val ir =
        """
          |(: a)
          |""".stripMargin.preprocessExpression.get.resolve

      ir shouldBe an[IR.Function.Lambda]
      ir.asInstanceOf[IR.Function.Lambda].body shouldBe an[IR.Type.Ascription]
    }

    "work for underscore arguments on the left" in {
      val ir =
        """
          |_ : A
          |""".stripMargin.preprocessExpression.get.resolve

      ir shouldBe an[IR.Function.Lambda]
      ir.asInstanceOf[IR.Function.Lambda].body shouldBe an[IR.Type.Ascription]
    }

    "work for underscore arguments on the right" in {
      val ir =
        """
          |a : _
          |""".stripMargin.preprocessExpression.get.resolve

      ir shouldBe an[IR.Function.Lambda]
      ir.asInstanceOf[IR.Function.Lambda].body shouldBe an[IR.Type.Ascription]
    }
  }

  "Resolution" should {
    "resolve type ascription" in {
      pending
    }

    "resolve context ascription" in {
      pending
    }

    "resolve error ascription" in {
      pending
    }

    "resolve subsumption" in {
      pending
    }

    "resolve equality" in {
      pending
    }

    "resolve concatenation" in {
      pending
    }

    "resolve union" in {
      pending
    }

    "resolve intersection" in {
      pending
    }

    "resolve subtraction" in {
      pending
    }
  }
}
