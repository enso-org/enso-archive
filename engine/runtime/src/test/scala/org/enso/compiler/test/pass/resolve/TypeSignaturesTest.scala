package org.enso.compiler.test.pass.resolve

import org.enso.compiler.Passes
import org.enso.compiler.context.{FreshNameSupply, InlineContext, ModuleContext}
import org.enso.compiler.core.IR
import org.enso.compiler.pass.{IRPass, PassConfiguration, PassManager}
import org.enso.compiler.pass.resolve.TypeSignatures
import org.enso.compiler.test.CompilerTest

class TypeSignaturesTest extends CompilerTest {

  // === Test Setup ===========================================================

  val passes = new Passes

  val precursorPasses: List[IRPass] = passes.getPrecursors(TypeSignatures).get

  val passConfiguration: PassConfiguration = PassConfiguration()

  implicit val passManager: PassManager =
    new PassManager(precursorPasses, passConfiguration)

  implicit class ResolveModule(ir: IR.Module) {
    def resolve(implicit moduleContext: ModuleContext): IR.Module = {
      TypeSignatures.runModule(ir, moduleContext)
    }
  }

  implicit class ResolveExpression(ir: IR.Expression) {
    def resolve(implicit inlineContext: InlineContext): IR.Expression = {
      TypeSignatures.runExpression(ir, inlineContext)
    }
  }

  def mkModuleContext: ModuleContext = {
    ModuleContext(freshNameSupply = Some(new FreshNameSupply))
  }

  def mkInlineContext: InlineContext = {
    InlineContext(freshNameSupply = Some(new FreshNameSupply))
  }

  // === The Tests ============================================================

  "Resolution of type signatures in modules" should {
    "associate signatures with method definitions" in {
      pending
    }

    "raise an error if a signature is divorced from its definition" in {
      pending
    }
  }

  "Resolution of type signatures for blocks" should {
    "associate signatures with bindings" in {
      pending
    }

    "raise an error if a signature is divorced from its definition" in {
      pending
    }
  }

  "Resolution of inline type signatures" should {
    "associate the signature with the typed expression" in {
      pending
    }
  }
}
