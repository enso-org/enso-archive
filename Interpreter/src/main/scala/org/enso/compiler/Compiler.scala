package org.enso.compiler

import com.oracle.truffle.api.TruffleFile
import com.oracle.truffle.api.source.Source
import org.enso.compiler.generate.AstToIr
import org.enso.compiler.ir.IR
import org.enso.flexer.Reader
import org.enso.interpreter.Constants
import org.enso.interpreter.Language
import org.enso.interpreter.node.ExpressionNode
import org.enso.interpreter.node.expression.literal.IntegerLiteralNode
import org.enso.interpreter.runtime.Module
import org.enso.interpreter.runtime.error.ModuleDoesNotExistException
import org.enso.interpreter.runtime.scope.ModuleScope
import org.enso.syntax.text.AST
import org.enso.syntax.text.Parser

import scala.collection.JavaConverters._
import scala.collection.mutable

// TODO [AA] Process as follows:
//  1. Traverse the output AST to transform it into the IR (needs the ability to
//  store and return arbitrary info in the AST) [DONE]
//  2. Validate the IR
//  3. Desugar the IR (pass by pass)
//  4. Analyse the IR (may be interleaved with the above)
//  5. Codegen to interpreter AST

/**
  * This class encapsulates the static transformation processes that take place
  * on source code, including parsing, desugaring, type-checking, static
  * analysis, and optimisation.
  */
class Compiler(
  val language: Language,
  val files: java.util.Map[String, Module]
) {

  val knownFiles: mutable.Map[String, Module] = files.asScala

  /**
    * Processes the provided language sources, registering any bindings in the
    * given scope.
    *
    * @param source the source code to be processed
    * @param scope the scope into which new bindings are registered
    * @return an interpreter node whose execution corresponds to the top-level
    *         executable functionality in the module corresponding to `source`.
    */
  def run(source: Source, scope: ModuleScope): ExpressionNode = {
    val parsedAST: AST  = parse(source)
    val desugaredIR: IR = translate(parsedAST)

    // FIXME [AA] Temporary, to prevent a billion errors while testing parsing
    new IntegerLiteralNode(10)

//    val parsed: AstModuleScope =
//      new EnsoParser().parseEnso(source.getCharacters.toString)
//    new ModuleScopeExpressionFactory(language, scope).run(parsed)
  }

  // TODO [AA] This needs to evolve to support scope execution

  // TODO [AA] Need to walk the AST and report the parse errors as they are
  //  encountered. The interpreter is not expected to execute on errored code.

  /**
    * Processes the language sources in the provided file, registering any
    * bindings in the given scope.
    *
    * @param file the file containing the source code
    * @param scope the scope into which new bindings are registered
    * @return an interpreter node whose execution corresponds to the top-level
    *         executable functionality in the module corresponding to `source`.
    */
  def run(file: TruffleFile, scope: ModuleScope): ExpressionNode = {
    run(Source.newBuilder(Constants.LANGUAGE_ID, file).build, scope)
  }

  /**
    * Processes the provided language sources, registering their bindings in a
    * new scope.
    *
    * @param source the source code to be processed
    * @return an interpreter node whose execution corresponds to the top-level
    *         executable functionality in the module corresponding to `source`.
    */
  def run(source: Source): ExpressionNode = {
    run(source, new ModuleScope)
  }

  /**
    * Processes the language sources in the provided file, registering any
    * bindings in a new scope.
    *
    * @param file the file containing the source code
    * @return an interpreter node whose execution corresponds to the top-level
    *         executable functionality in the module corresponding to `source`.
    */
  def run(file: TruffleFile): ExpressionNode = {
    run(Source.newBuilder(Constants.LANGUAGE_ID, file).build)
  }

  /**
    * Finds and processes a language source by its qualified name.
    *
    * The results of this operation are cached internally so we do not need to
    * process the same source file multiple times.
    *
    * @param qualifiedName the qualified name of the module
    * @return the scope containing all definitions in the requested module
    */
  def requestProcess(qualifiedName: String): ModuleScope = {
    knownFiles.get(qualifiedName) match {
      case Some(module) => module.requestParse(language.getCurrentContext)
      case None         => throw new ModuleDoesNotExistException(qualifiedName)
    }
  }

  /**
    * Parses the provided language sources.
    *
    * @param source the code to parse
    * @return an AST representation of `source`
    */
  def parse(source: Source): AST = {
    val parser: Parser = Parser()
    val unresolvedAST: AST.Module =
      parser.run(new Reader(source.getCharacters.toString))
    val resolvedAST: AST.Module = parser.dropMacroMeta(unresolvedAST)

    resolvedAST
  }

  /**
    * Lowers the input AST to the compiler's high-level intermediate
    * representation.
    *
    * @param sourceAST the parser AST input
    * @return an IR representation with a 1:1 mapping to the parser AST
    *         constructs
    */
  def translate(sourceAST: AST): IR = AstToIr.process(sourceAST)
}
