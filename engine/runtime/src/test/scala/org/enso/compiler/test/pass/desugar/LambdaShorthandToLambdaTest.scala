package org.enso.compiler.test.pass.desugar

import org.enso.compiler.context.{FreshNameSupply, InlineContext}
import org.enso.compiler.core.IR
import org.enso.compiler.pass.desugar.{
  GenerateMethodBodies,
  LambdaShorthandToLambda,
  OperatorToFunction,
  SectionsToBinOp
}
import org.enso.compiler.pass.{IRPass, PassConfiguration, PassManager}
import org.enso.compiler.test.CompilerTest

class LambdaShorthandToLambdaTest extends CompilerTest {

  // === Test Setup ===========================================================

  val passes: List[IRPass] = List(
    GenerateMethodBodies,
    SectionsToBinOp,
    OperatorToFunction
  )

  val passConfiguration: PassConfiguration = PassConfiguration()

  implicit val passManager: PassManager =
    new PassManager(passes, passConfiguration)

  /** Adds an extension method for running desugaring on the input IR.
    *
    * @param ir the IR to desugar
    */
  implicit class DesugarExpression(ir: IR.Expression) {

    /** Runs section desugaring on [[ir]].
      *
      * @param inlineContext the inline context in which the desugaring takes
      *                      place
      * @return [[ir]], with all sections desugared
      */
    def desugar(implicit inlineContext: InlineContext): IR.Expression = {
      LambdaShorthandToLambda.runExpression(ir, inlineContext)
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

  "Desugaring of underscore arguments" should {
    "Work for simple applications with underscore args" in {
      implicit val ctx: InlineContext = mkInlineContext

      val ir =
        """
          |foo a _ b _
          |""".stripMargin.toIrExpression.get.desugar

      ir shouldBe an[IR.Function.Lambda]
      val irFn = ir.asInstanceOf[IR.Function.Lambda]
      val irFnArgName =
        irFn.arguments.head.asInstanceOf[IR.DefinitionArgument.Specified].name

      irFn.body shouldBe an[IR.Function.Lambda]
      val irFnNested = irFn.body.asInstanceOf[IR.Function.Lambda]
      val irFnNestedArgName = irFnNested.arguments.head
        .asInstanceOf[IR.DefinitionArgument.Specified]
        .name

      irFnNested.body shouldBe an[IR.Application.Prefix]
      val body = irFn.body
        .asInstanceOf[IR.Function.Lambda]
        .body
        .asInstanceOf[IR.Application.Prefix]

      val arg2Name = body
        .arguments(1)
        .asInstanceOf[IR.CallArgument.Specified]
        .value
        .asInstanceOf[IR.Name.Literal]
      val arg4Name = body
        .arguments(3)
        .asInstanceOf[IR.CallArgument.Specified]
        .value
        .asInstanceOf[IR.Name.Literal]

      irFnArgName.name shouldEqual arg2Name.name
      irFnNestedArgName.name shouldEqual arg4Name.name
    }

    "Work for named applications of underscore args" in {
      implicit val ctx: InlineContext = mkInlineContext

      val ir =
        """
          |foo (a = _) b _
          |""".stripMargin.preprocessExpression.get.desugar

      ir shouldBe an[IR.Function.Lambda]
      val irFn = ir.asInstanceOf[IR.Function.Lambda]
      val irFnArgName =
        irFn.arguments.head.asInstanceOf[IR.DefinitionArgument.Specified].name

      irFn.body shouldBe an[IR.Function.Lambda]
      val irFnNested = irFn.body.asInstanceOf[IR.Function.Lambda]
      val irFnNestedArgName =
        irFnNested.arguments.head
          .asInstanceOf[IR.DefinitionArgument.Specified]
          .name

      irFnNested.body shouldBe an[IR.Application.Prefix]
      val app = irFnNested.body.asInstanceOf[IR.Application.Prefix]
      val arg1Name =
        app.arguments.head
          .asInstanceOf[IR.CallArgument.Specified]
          .value
          .asInstanceOf[IR.Name.Literal]
      val arg3Name =
        app
          .arguments(2)
          .asInstanceOf[IR.CallArgument.Specified]
          .value
          .asInstanceOf[IR.Name.Literal]

      irFnArgName.name shouldEqual arg1Name.name
      irFnNestedArgName.name shouldEqual arg3Name.name
    }

    "Work if the function in an application is an underscore arg" in {
      implicit val ctx: InlineContext = mkInlineContext

      val ir =
        """
          |_ a b
          |""".stripMargin.toIrExpression.get.desugar

      ir shouldBe an[IR.Function.Lambda]
      val irFn = ir.asInstanceOf[IR.Function.Lambda]
      val irFnArgName =
        irFn.arguments.head.asInstanceOf[IR.DefinitionArgument.Specified].name

      irFn.body shouldBe an[IR.Application.Prefix]
      val app = irFn.body.asInstanceOf[IR.Application.Prefix]

      val fnName = app.function.asInstanceOf[IR.Name.Literal]

      irFnArgName.name shouldEqual fnName.name
    }

    "Work with mixfix functions" in {
      implicit val ctx: InlineContext = mkInlineContext

      val ir =
        """
          |if _ then a
          |""".stripMargin.toIrExpression.get.desugar

      ir shouldBe an[IR.Function.Lambda]
      val irFn = ir.asInstanceOf[IR.Function.Lambda]
      val irFnArgName =
        irFn.arguments.head.asInstanceOf[IR.DefinitionArgument.Specified].name

      irFn.body shouldBe an[IR.Application.Prefix]
      val app = irFn.body.asInstanceOf[IR.Application.Prefix]
      val arg1Name = app.arguments.head
        .asInstanceOf[IR.CallArgument.Specified]
        .value
        .asInstanceOf[IR.Name.Literal]

      irFnArgName.name shouldEqual arg1Name.name
    }

    "Work for an underscore scrutinee in a case expression" in {
      implicit val ctx: InlineContext = mkInlineContext

      val ir =
        """
          |case _ of
          |    Nil -> 0
          |""".stripMargin.preprocessExpression.get.desugar

      ir shouldBe an[IR.Function.Lambda]

      val irLam = ir.asInstanceOf[IR.Function.Lambda]
      irLam.arguments.length shouldEqual 1

      val lamArgName =
        irLam.arguments.head.asInstanceOf[IR.DefinitionArgument.Specified].name

      val lamBody = irLam.body.asInstanceOf[IR.Case.Expr]

      lamBody.scrutinee shouldBe an[IR.Name.Literal]
      lamBody.scrutinee
        .asInstanceOf[IR.Name.Literal]
        .name shouldEqual lamArgName.name
    }
  }

  "Nested underscore arguments" should {
    "work for applications" in {
      implicit val ctx: InlineContext = mkInlineContext

      val ir =
        """
          |a _ (fn _ c)
          |""".stripMargin.toIrExpression.get.desugar

      ir shouldBe an[IR.Function.Lambda]
      ir.asInstanceOf[IR.Function.Lambda]
        .body shouldBe an[IR.Application.Prefix]
      val irBody = ir
        .asInstanceOf[IR.Function.Lambda]
        .body
        .asInstanceOf[IR.Application.Prefix]

      irBody
        .arguments(1)
        .asInstanceOf[IR.CallArgument.Specified]
        .value shouldBe an[IR.Function.Lambda]
      val lamArg = irBody
        .arguments(1)
        .asInstanceOf[IR.CallArgument.Specified]
        .value
        .asInstanceOf[IR.Function.Lambda]
      val lamArgArgName =
        lamArg.arguments.head.asInstanceOf[IR.DefinitionArgument.Specified].name

      lamArg.body shouldBe an[IR.Application.Prefix]
      val lamArgBody = lamArg.body.asInstanceOf[IR.Application.Prefix]
      val lamArgBodyArg1Name = lamArgBody.arguments.head
        .asInstanceOf[IR.CallArgument.Specified]
        .value
        .asInstanceOf[IR.Name.Literal]

      lamArgArgName.name shouldEqual lamArgBodyArg1Name.name
    }

    "work in named applications" in {
      implicit val ctx: InlineContext = mkInlineContext

      val ir =
        """
          |a _ (fn (t = _) c)
          |""".stripMargin.toIrExpression.get.desugar

      ir shouldBe an[IR.Function.Lambda]
      ir.asInstanceOf[IR.Function.Lambda]
        .body shouldBe an[IR.Application.Prefix]
      val irBody = ir
        .asInstanceOf[IR.Function.Lambda]
        .body
        .asInstanceOf[IR.Application.Prefix]

      irBody
        .arguments(1)
        .asInstanceOf[IR.CallArgument.Specified]
        .value shouldBe an[IR.Function.Lambda]
      val lamArg = irBody
        .arguments(1)
        .asInstanceOf[IR.CallArgument.Specified]
        .value
        .asInstanceOf[IR.Function.Lambda]
      val lamArgArgName =
        lamArg.arguments.head.asInstanceOf[IR.DefinitionArgument.Specified].name

      lamArg.body shouldBe an[IR.Application.Prefix]
      val lamArgBody = lamArg.body.asInstanceOf[IR.Application.Prefix]
      val lamArgBodyArg1Name = lamArgBody.arguments.head
        .asInstanceOf[IR.CallArgument.Specified]
        .value
        .asInstanceOf[IR.Name.Literal]

      lamArgArgName.name shouldEqual lamArgBodyArg1Name.name
    }

    "work in function argument defaults" in {
      implicit val ctx: InlineContext = mkInlineContext

      val ir =
        """
          |a -> (b = f _ 1) -> f a
          |""".stripMargin.toIrExpression.get.desugar

      ir shouldBe an[IR.Function.Lambda]
      val bArgFn = ir
        .asInstanceOf[IR.Function.Lambda]
        .body
        .asInstanceOf[IR.Function.Lambda]
      val bArg1 =
        bArgFn.arguments.head.asInstanceOf[IR.DefinitionArgument.Specified]

      bArg1.defaultValue shouldBe defined
      bArg1.defaultValue.get shouldBe an[IR.Function.Lambda]
      val default = bArg1.defaultValue.get.asInstanceOf[IR.Function.Lambda]
      val defaultArgName = default.arguments.head
        .asInstanceOf[IR.DefinitionArgument.Specified]
        .name

      default.body shouldBe an[IR.Application.Prefix]
      val defBody = default.body.asInstanceOf[IR.Application.Prefix]
      val defBodyArg1Name = defBody.arguments.head
        .asInstanceOf[IR.CallArgument.Specified]
        .value
        .asInstanceOf[IR.Name.Literal]

      defaultArgName.name shouldEqual defBodyArg1Name.name
    }

    "work for case expressions" in {
      implicit val ctx: InlineContext = mkInlineContext

      val ir =
        """
          |case _ of
          |    Nil -> f _ b
          |""".stripMargin.toIrExpression.get.desugar

      ir shouldBe an[IR.Function.Lambda]
      val nilBranch = ir
        .asInstanceOf[IR.Function.Lambda]
        .body
        .asInstanceOf[IR.Case.Expr]
        .branches
        .head
        .asInstanceOf[IR.Case.Branch]
        .expression
        .asInstanceOf[IR.Function.Lambda]

      nilBranch.body shouldBe an[IR.Function.Lambda]
      val nilBody = nilBranch.body.asInstanceOf[IR.Function.Lambda]
      val nilBodyArgName =
        nilBody.arguments.head
          .asInstanceOf[IR.DefinitionArgument.Specified]
          .name

      nilBody.body shouldBe an[IR.Application.Prefix]
      val nilBodyBody = nilBody.body.asInstanceOf[IR.Application.Prefix]
      val nilBodyBodyArg1Name = nilBodyBody.arguments.head
        .asInstanceOf[IR.CallArgument.Specified]
        .value
        .asInstanceOf[IR.Name.Literal]

      nilBodyArgName.name shouldEqual nilBodyBodyArg1Name.name
    }
  }
}
