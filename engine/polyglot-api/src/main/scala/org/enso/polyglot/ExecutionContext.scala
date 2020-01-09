package org.enso.polyglot
import java.io.File

import org.graalvm.polyglot.{Context, Source}

/**
  * Exposes language specific aliases for generic polyglot context operations.
  * @param context the Graal polyglot context to use.
  */
class ExecutionContext(val context: Context) {

  /**
    * Evaluates provided code string as a new module.
    *
    * @param code the code to evaluate.
    * @param moduleName the name for the newly parsed module.
    * @return the module representing evaluated code.
    */
  def evalModule(code: String, moduleName: String): Module = {
    val source = Source
      .newBuilder(LanguageInfo.ID, code, moduleName)
      .build()
    new Module(context.eval(source))
  }

  def evalModule(codeFile: File): Module = {
    val source = Source.newBuilder(LanguageInfo.ID, codeFile).build
    new Module(context.eval(source))
  }

  /**
    * @return the top scope of Enso execution context
    */
  def getTopScope: TopScope = {
    new TopScope(context.getBindings(LanguageInfo.ID))
  }
}