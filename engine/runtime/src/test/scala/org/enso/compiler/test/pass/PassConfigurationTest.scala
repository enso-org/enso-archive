package org.enso.compiler.test.pass

import org.enso.compiler.context.{InlineContext, ModuleContext}
import org.enso.compiler.core.IR
import org.enso.compiler.pass.PassConfiguration._
import org.enso.compiler.pass.{IRPass, PassConfiguration}
import org.enso.compiler.test.CompilerTest
import shapeless.test.illTyped

class PassConfigurationTest extends CompilerTest {

  // === Test Utilities =======================================================

  case object TestPass1 extends IRPass {
    override type Metadata = IR.Metadata.Empty
    override type Config   = Configuration1

    override def runModule(
      ir: IR.Module,
      moduleContext: ModuleContext
    ): IR.Module = ir

    override def runExpression(
      ir: IR.Expression,
      inlineContext: InlineContext
    ): IR.Expression = ir

    sealed case class Configuration1() extends IRPass.Configuration {
      override var shouldWriteToContext: Boolean = false
    }
  }

  case object TestPass2 extends IRPass {
    override type Metadata = IR.Metadata.Empty
    override type Config   = Configuration2

    override def runModule(
      ir: IR.Module,
      moduleContext: ModuleContext
    ): IR.Module = ir

    override def runExpression(
      ir: IR.Expression,
      inlineContext: InlineContext
    ): IR.Expression = ir

    sealed case class Configuration2() extends IRPass.Configuration {
      override var shouldWriteToContext: Boolean = false
    }
  }

  // === The Tests ============================================================

  "The pass configuration" should {
    "allow adding configurations" in {
      val config = new PassConfiguration

      val config1 = TestPass1.Configuration1()
      val config2 = TestPass2.Configuration2()

      config.update(TestPass1)(config1)
      config.update(TestPass2)(config2)

      config.get(TestPass1) shouldEqual Some(config1)
      config.get(TestPass2) shouldEqual Some(config2)
    }

    "allow getting configurations" in {
      val config  = new PassConfiguration
      val config1 = TestPass1.Configuration1()

      config.update(TestPass1)(config1)
      config.get(TestPass1) shouldEqual Some(config1)
    }

    "allow updating configurations" in {
      val config = new PassConfiguration

      val config1 = TestPass1.Configuration1()
      val config2 = TestPass1.Configuration1()

      config.update(TestPass1)(config1)
      config.get(TestPass1) shouldEqual Some(config1)
      config.update(TestPass1)(config2)
      config.get(TestPass1) shouldEqual Some(config2)
    }

    "allow removing configurations" in {
      val config = new PassConfiguration

      val config1 = TestPass1.Configuration1()
      config.update(TestPass1)(config1)

      config.remove(TestPass1) shouldEqual Some(config1)
      config.get(TestPass1) shouldEqual None
    }

    "compare equal when containing the same configurations" in {
      val config1 = new PassConfiguration
      val config2 = new PassConfiguration

      config1 shouldEqual config2

      config1.update(TestPass1)(TestPass1.Configuration1())
      config2.update(TestPass1)(TestPass1.Configuration1())

      config1 shouldEqual config2
    }

    "enforce safe construction" in {
      val test1 = TestPass1 -->> TestPass1.Configuration1()
      val test2 = TestPass2 -->> TestPass2.Configuration2()

      PassConfiguration(test1, test2)

      illTyped("TestPass1 -->> TestPass2.Configuration2()")
      illTyped("PassConfiguration(test1, (1, 1))")
    }
  }
}
