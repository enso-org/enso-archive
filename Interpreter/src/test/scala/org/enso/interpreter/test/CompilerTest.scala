package org.enso.interpreter.test

import java.io.StringReader
import java.util

import com.oracle.truffle.api.source.Source
import org.enso.compiler.Compiler
import org.enso.compiler.ir.HLIR
import org.enso.interpreter.Constants
import org.enso.interpreter.runtime.Module
import org.enso.syntax.text.AST
import org.scalatest.FlatSpec
import org.scalatest.Matchers

/**
  * A unified framework for writing tests for the compiler portion of the Enso
  * engine.
  */
trait CompilerTest extends FlatSpec with Matchers with AnalysisRunner
trait AnalysisRunner {
  // FIXME [AA] This should actually use the interpreter, but for now we just
  //  pass nulls in at construction
  val compiler: Compiler =
    new Compiler(null, new util.HashMap[String, Module]())

  /**
    * This method tests the translation of source code into the high-level IR.
    *
    * @param code the input code
    * @return the HLIR that results from the input code
    */
  def translate(code: String): HLIR.IR = {
    val ast: AST = compiler.parse(
      Source
        .newBuilder(Constants.LANGUAGE_ID, new StringReader(code), "test")
        .build()
    )

    compiler.translate(ast)
  }
}

