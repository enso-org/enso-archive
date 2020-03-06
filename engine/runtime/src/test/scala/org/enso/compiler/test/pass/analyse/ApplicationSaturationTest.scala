package org.enso.compiler.test.pass.analyse

import org.enso.compiler.core.IR
import org.enso.compiler.core.IR.Metadata
import org.enso.compiler.pass.analyse.ApplicationSaturation
import org.enso.compiler.pass.analyse.ApplicationSaturation.{CallSaturation, FunctionSpec, PassConfiguration}
import org.enso.compiler.test.CompilerTest
import org.enso.interpreter.node.ExpressionNode
import org.enso.interpreter.runtime.callable.argument.CallArgument

import scala.annotation.unused
import scala.reflect.ClassTag

class ApplicationSaturationTest extends CompilerTest {

  // === Utilities ============================================================

  /** Generates n arguments that _do not_ contain function applications
    * themselves.
    *
    * @param n the number of arguments to generate
    * @param positional whether or not the arguments should be generated by name
    *                   or positionally
    * @return a list containing `n` arguments
    */
  def genNArgs(n: Int, positional: Boolean = true): List[IR.CallArgument] = {
    val name = if (positional) {
      None
    } else {
      Some(IR.Name.Literal("a", None))
    }

    List.fill(n)(IR.CallArgument.Specified(name, IR.Empty(None), None))
  }

  // === Test Setup ===========================================================

  // The functions are unused, so left undefined for ease of testing
  def dummyFn(@unused args: List[CallArgument]): ExpressionNode = ???

  val knownFunctions: PassConfiguration = Map(
    "+"   -> FunctionSpec(2, dummyFn),
    "baz" -> FunctionSpec(3, dummyFn),
    "foo" -> FunctionSpec(4, dummyFn)
  )

  // === The Tests ============================================================

  "Known applications" should {
    val plusFn = IR.Application.Prefix(
      IR.Name.Literal("+", None),
      genNArgs(2),
      hasDefaultsSuspended = false,
      None
    )

    val bazFn = IR.Application.Prefix(
      IR.Name.Literal("baz", None),
      genNArgs(2),
      hasDefaultsSuspended = false,
      None
    )

    val fooFn = IR.Application.Prefix(
      IR.Name.Literal("foo", None),
      genNArgs(5),
      hasDefaultsSuspended = false,
      None
    )

    val fooFnByName = IR.Application.Prefix(
      IR.Name.Literal("foo", None),
      genNArgs(4, positional = false),
      hasDefaultsSuspended = false,
      None
    )

    "be tagged with full saturation where possible" in {
      val resultIR = ApplicationSaturation(knownFunctions).runExpression(plusFn)

      resultIR.getMetadata[CallSaturation].foreach {
        case _: CallSaturation.Exact => succeed
        case _                       => fail
      }
    }

    "be tagged with partial saturation where possible" in {
      val resultIR = ApplicationSaturation(knownFunctions).runExpression(bazFn)
      val expected = Some(CallSaturation.Partial(1))

      resultIR.getMetadata[CallSaturation] shouldEqual expected
    }

    "be tagged with over saturation where possible" in {
      val resultIR = ApplicationSaturation(knownFunctions).runExpression(fooFn)
      val expected = Some(CallSaturation.Over(1))

      resultIR.getMetadata[CallSaturation] shouldEqual expected
    }

    "be tagged with by name if applied by name" in {
      val resultIR =
        ApplicationSaturation(knownFunctions).runExpression(fooFnByName)
      val expected = Some(CallSaturation.ExactButByName())

      resultIR.getMetadata[CallSaturation] shouldEqual expected
    }
  }

  "Unknown applications" should {
    val unknownFn = IR.Application.Prefix(
      IR.Name.Literal("unknown", None),
      genNArgs(10),
      hasDefaultsSuspended = false,
      None
    )

    "be tagged with unknown saturation" in {
      val resultIR =
        ApplicationSaturation(knownFunctions).runExpression(unknownFn)
      val expected = Some(CallSaturation.Unknown())

      resultIR.getMetadata[CallSaturation] shouldEqual expected
    }
  }

  "Known applications containing known applications" should {
    val empty = IR.Empty(None)
    val knownPlus = IR.Application.Prefix(
      IR.Name.Literal("+", None),
      genNArgs(2),
      hasDefaultsSuspended = false,
      None
    )

    val undersaturatedPlus = IR.Application.Prefix(
      IR.Name.Literal("+", None),
      genNArgs(1),
      hasDefaultsSuspended = false,
      None
    )

    val oversaturatedPlus = IR.Application.Prefix(
      IR.Name.Literal("+", None),
      genNArgs(3),
      hasDefaultsSuspended = false,
      None
    )

    implicit class InnerMeta(ir: IR.Expression) {
      def getInnerMetadata[T <: Metadata : ClassTag]: Option[T] = {
        ir.asInstanceOf[IR.Application.Prefix]
          .arguments
          .head
          .asInstanceOf[IR.CallArgument.Specified]
          .value
          .getMetadata[T]
      }
    }

    def outerPlus(argExpr: IR.Expression): IR.Application.Prefix = {
      IR.Application.Prefix(
        IR.Name.Literal("+", None),
        List(
          IR.CallArgument.Specified(None, argExpr, None),
          IR.CallArgument.Specified(None, empty, None)
        ),
        hasDefaultsSuspended = false,
        None
      )
    }

    "have fully saturated applications tagged correctly" in {
      val result =
        ApplicationSaturation(knownFunctions).runExpression(
          outerPlus(knownPlus)
        )

      // The outer should be reported as fully saturated
      result.getMetadata[CallSaturation].foreach {
        case _: CallSaturation.Exact => succeed
        case _                       => fail
      }

      // The inner should be reported as fully saturated
      result.getInnerMetadata[CallSaturation].foreach {
        case _: CallSaturation.Exact => succeed
        case _                       => fail
      }
    }

    "have non-fully saturated applications tagged correctly" in {
      val result =
        ApplicationSaturation(knownFunctions).runExpression(
          outerPlus(undersaturatedPlus)
        )
      val expectedInnerMeta = CallSaturation.Partial(1)

      // The outer should be reported as fully saturated
      result.getMetadata[CallSaturation].foreach {
        case _: CallSaturation.Exact => succeed
        case _                       => fail
      }

      // The inner should be reported as under saturateD
      result
        .getInnerMetadata[CallSaturation]
        .foreach(t => t shouldEqual expectedInnerMeta)
    }

    "have a mixture of application saturations tagged correctly" in {
      val result =
        ApplicationSaturation(knownFunctions).runExpression(
          outerPlus(oversaturatedPlus)
        )
      val expectedInnerMeta = CallSaturation.Over(1)

      // The outer should be reported as fully saturated
      result.getMetadata[CallSaturation].foreach {
        case _: CallSaturation.Exact => succeed
        case _                       => fail
      }

      // The inner should be reported as under saturateD
      result
        .getInnerMetadata[CallSaturation]
        .foreach(t => t shouldEqual expectedInnerMeta)
    }
  }

  "Shadowed known functions" should {
    "not be tagged as if they are known" in {}
  }

}
