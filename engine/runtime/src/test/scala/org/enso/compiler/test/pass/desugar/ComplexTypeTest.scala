package org.enso.compiler.test.pass.desugar

import org.enso.compiler.context.ModuleContext
import org.enso.compiler.core.IR
import org.enso.compiler.pass.desugar.ComplexType
import org.enso.compiler.pass.{IRPass, PassConfiguration, PassManager}
import org.enso.compiler.test.CompilerTest

class ComplexTypeTest extends CompilerTest {

  // === Test Setup ===========================================================

  val precursorPasses: List[IRPass] = List()
  val passConfig: PassConfiguration = PassConfiguration()

  implicit val passManager: PassManager =
    new PassManager(precursorPasses, passConfig)

  /** Adds an extension method to run complex type desugaring on an
    * [[IR.Module]].
    *
    * @param ir the module to run desugaring on
    */
  implicit class DesugarModule(ir: IR.Module) {

    /** Runs desugaring on a module.
      *
      * @param moduleContext the module context in which desugaring is taking
      *                      place
      * @return [[ir]], with any complex type definitions desugared
      */
    def desugar(implicit moduleContext: ModuleContext): IR.Module = {
      ComplexType.runModule(ir, moduleContext)
    }
  }

  /** Creates a defaulted module context.
    *
    * @return a defaulted module context
    */
  def mkModuleContext: ModuleContext = {
    ModuleContext()
  }

  // === The Tests ============================================================

  "Valid complex types" should {
    "have their atoms desugared to top-level atoms" in {
      pending
    }

    "have their methods desugared to methods on the defined atoms" in {
      pending
    }

    "have their methods desugared to methods on included atoms" in {
      pending
    }
  }
}
