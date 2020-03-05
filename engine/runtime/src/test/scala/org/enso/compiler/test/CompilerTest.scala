package org.enso.compiler.test

import org.enso.compiler.codegen.AstToIR
import org.enso.compiler.core.IR
import org.enso.flexer.Reader
import org.enso.syntax.text.{AST, Parser}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

trait CompilerRunner {
  def toIR(source: String): IR = {
    val parser: Parser = Parser()
    val unresolvedAST: AST.Module =
      parser.run(new Reader(source))
    val resolvedAST: AST.Module = parser.dropMacroMeta(unresolvedAST)

    AstToIR.translate(resolvedAST)
  }
}

trait CompilerTest extends AnyWordSpecLike with Matchers with CompilerRunner {

  // === IR Testing Utils =====================================================

  /** Hoists the provided expression into the body of a method.
   *
   * @param ir the expression to hoist
   * @return a method containing `ir` as its body
   */
  def asMethod(ir: IR.Expression): IR.Module.Scope.Definition.Method = {
    IR.Module.Scope.Definition.Method("TestType", "testMethod", ir, None)
  }

  /** Hoists the provided expression as the default value of an atom argument.
   *
   * @param ir the expression to hoist
   * @return an atom with one argument `arg` with default value `ir`
   */
  def asAtomDefaultArg(ir: IR.Expression): IR.Module.Scope.Definition.Atom = {
    IR.Module.Scope.Definition.Atom(
      "TestAtom",
      List(
        IR.DefinitionArgument
          .Specified("arg", Some(ir), suspended = false, None)
      ),
      None
    )
  }

  /** Creates a module containing both an atom and a method that use the
   * provided expression.
   *
   * The expression is used in the default for an atom argument, as in
   * [[asAtomDefaultArg()]], and in the body of a method, as in [[asMethod()]].
   *
   * @param expr the expression
   * @return a module containing an atom def and method def using `expr`
   */
  def moduleDefsFrom(expr: IR.Expression): IR.Module = {
    IR.Module(List(), List(asAtomDefaultArg(expr), asMethod(expr)), None)
  }
}
